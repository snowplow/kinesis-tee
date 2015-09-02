#!/usr/bin/env ruby

require 'shellwords'
require 'json'
require 'httparty'

def perror(msg)
    puts "Error: #{msg}"
    exit 1
end 

def get_version(str)
    str.match(/(\d+\.\d+\.\d+)/)[1]
end

def get_project_version(base_dir)
    ver_output = ""
    Dir.chdir(base_dir){
        ver_output = %x[sbt version]
    }
    
    get_version(ver_output)
end

def exec(args) 
    safe_args = args.map { |arg| Shellwords.escape arg }.join " "
    res = system "#{safe_args} > /dev/null"
    if !res then
        perror "Failed to execute '#{safe_args}' process exited abnormally"
    end
end

def unzip_file(file, destination)
    exec ["7z", "x", "#{file}", "-aoa", "-o#{destination}"]
end

def zip_dir(directory, archive_name)
    exec ["7z", "a", "-tzip", "-mx=9", "#{archive_name}", "#{directory}"]
end

def create_bintray_version(org, user, key, version, repo, package_name)
    
    bintray_repo = "#{org}/#{repo}"

    request = { :name => "#{version}", :desc => "Release of '#{package_name}'" }.to_json
    auth = {:username => user, :password => key}

    url = "https://api.bintray.com/packages/#{bintray_repo}/#{package_name}/versions"

    response = HTTParty.post(url,
                             { 
                                 :body => request,
                                 :basic_auth => auth,
                                 :headers => { 'Content-Type' => 'application/json', 'Accept' => 'application/json' }
                             })

    case response.code
        when 200..300 ## ok 
            puts "Version #{version} of #{package_name} created"
        when 409
            puts "Version #{version} of #{package_name} exists, skipping..."
        when 500..600
            perror "Failed to create version #{version} of #{package_name} (error code: #{response.code})"
        else 
            perror "Unknown error creating version #{version} of #{package_name} (error code: #{response.code})"
    end
end

def publish_to_bintray(org, user, key, file, repo, package, version)

    bintray_repo = "#{org}/#{repo}"
    
    puts "Publishing #{file}"

    url = "https://api.bintray.com/content/#{bintray_repo}/#{package}/#{version}/#{file}?publish=1&override=1"

    upload_file = File.new(file, 'rb').read
    def upload_file.bytesize; self.size; end
    auth = {:username => user, :password => key}

    response = HTTParty.put(url,
                            { 
                                :headers => {"Content-Type"=>"application/octet-stream"}, 
                                :body => upload_file,
                                :basic_auth => auth
                            })
    
    case response.code
        when 200..300
            puts "File uploaded!"
        else
            perror "File failed to upload (error code: #{response.code})"
    end
end

def upload_to_bintray(org, user, key, file, version, repo, package_name)

    puts "Uploading '#{file}' to Bintray"

    create_bintray_version org, user, key, version, repo, package_name

    publish_to_bintray org, user, key, file, repo, package_name, version

end

# check versions

base_dir = ENV['TRAVIS_BUILD_DIR']
target_version = ARGV[0]

if base_dir.nil? 
    perror "TRAVIS_BUILD_DIR not set"
end    

actual_version = get_project_version(base_dir)

if actual_version != get_version(target_version)
    perror "Tag version '#{target_version}' doesn't match project version '#{actual_version}'"
end

assembled_jar = "#{base_dir}/target/scala-2.11/kinesis-tee-#{get_version(target_version)}.jar"
target_dir = "#{base_dir}/deploy/gordon/kinesis-tee/kinesis-tee-app/kinesis-tee-code"


if !File.exist?(assembled_jar)
    perror "Cannot find build artifact in '#{assembled_jar}'"
end

if !Dir.exist?(target_dir)
    perror "Gordon target directory '#{target_dir}' doesn't exist"
end

bintray_user = ENV['BINTRAY_SNOWPLOW_GENERIC_USER']
bintray_api_key = ENV['BINTRAY_SNOWPLOW_GENERIC_API_KEY']

if bintray_user.nil? 
    perror "Cannot find required field: BINTRAY_SNOWPLOW_GENERIC_USER"
end

if bintray_api_key.nil?
    perror "Cannot find required field: BINTRAY_SNOWPLOW_GENERIC_API_KEY"
end

# unzip assembled jar
# copy into deploy/gordon/kinesis-tee/kinesis-tee-app/kinesis-tee-code

unzip_file(assembled_jar, target_dir)

zip_target_dir = base_dir+"/deploy/gordon/*"
archive_name = "kinesis_tee_#{target_version.gsub(/-/, '_')}.zip"

# # zip everything there up
zip_dir(zip_target_dir, archive_name)

# # ship to bintray

upload_to_bintray "snowplow", bintray_user, bintray_api_key, archive_name, target_version, "snowplow-generic", "kinesis-tee"
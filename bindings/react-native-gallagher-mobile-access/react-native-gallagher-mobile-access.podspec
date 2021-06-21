require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

# Change this to point to the relative place where you've put the GallagherMobileAccess static library files
native_lib_path = "/Users/orione/Dev/TMP/lib"

Pod::Spec.new do |s|
  s.name         = "react-native-gallagher-mobile-access"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]

  s.platforms    = { :ios => "11.0" }
  s.source       = { :git => "", :tag => "#{s.version}" }

  s.source_files = "ios/**/*.{h,m,mm,swift}"

  s.xcconfig = { 
    "LIBRARY_SEARCH_PATHS[sdk=iphoneos*]" => "$(inherited) #{native_lib_path}/iphoneos",
    "LIBRARY_SEARCH_PATHS[sdk=iphonesimulator*]" => "$(inherited) #{native_lib_path}/iphonesimulator",
    "SWIFT_INCLUDE_PATHS" => "$(inherited) #{native_lib_path}",
    "OTHER_LDFLAGS" => "-lGallagherMobileAccess"
  }

  s.dependency "React-Core"
end

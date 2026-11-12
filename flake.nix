{
  description = "Jean-Calcul Android E2E development environment (Linux x86_64)";
  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  outputs = { self, nixpkgs }: let
    system = "x86_64-linux";
    pkgs = import nixpkgs { inherit system; config = {
      allowUnfree = true;
      android_sdk.accept_license = true;
    }; };
    android = pkgs.androidenv.composeAndroidPackages {
      # Compile against 37, but download only the API 35 test image.
      repo = let repository = builtins.fromJSON (builtins.readFile
        "${nixpkgs}/pkgs/development/mobile/androidenv/repo.json");
      in repository // { images = { "35" = repository.images."35"; }; };
      includeCmake = false;
      platformVersions = [ "37.0" "35" ];
      buildToolsVersions = [ "37.0.0" ];
      includeEmulator = true;
      includeSystemImages = true;
      systemImageTypes = [ "google_apis" ];
      abiVersions = [ "x86_64" ];
      includeSources = false;
    };
    sdk = "${android.androidsdk}/libexec/android-sdk";
  in {
    devShells.${system}.default = pkgs.mkShell {
      packages = [ pkgs.jdk25 pkgs.python3 android.androidsdk ];
      JAVA_HOME = "${pkgs.jdk25}";
      ANDROID_HOME = sdk;
      ANDROID_SDK_ROOT = sdk;
      GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdk}/build-tools/37.0.0/aapt2";
    };
  };
}

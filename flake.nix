{
  description = "Jean-Calcul Android development environment (Linux x86_64)";
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
      buildToolsVersions = [ "37.0.0" "36.0.0" ];
      includeEmulator = true;
      includeSystemImages = true;
      systemImageTypes = [ "google_apis" ];
      abiVersions = [ "x86_64" ];
      includeSources = false;
    };
    sdk = "${android.androidsdk}/libexec/android-sdk";

    # Google's Android CLI (`android`) ships generic glibc binaries that
    # self-update under ~/.android/bin. NixOS cannot exec them directly, so
    # they run inside an FHS sandbox. The official launcher is fetched once
    # into ~/.local/share/android-cli; `android update` keeps the CLI current.
    android-cli = pkgs.buildFHSEnv {
      name = "android";
      targetPkgs = p: with p; [
        bashInteractive coreutils curl git unzip which
        glibc zlib stdenv.cc.cc.lib ncurses5 expat libuuid
        fontconfig freetype dejavu_fonts
        libGL libx11 libxext libxrender libxi libxtst
      ];
      runScript = pkgs.writeShellScript "android-cli-launcher" ''
        set -eu
        dir="''${XDG_DATA_HOME:-$HOME/.local/share}/android-cli"
        launcher="$dir/android"
        if [ ! -x "$launcher" ]; then
          mkdir -p "$dir"
          echo "android: fetching the official launcher into $dir" >&2
          curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/android -o "$launcher.tmp"
          chmod +x "$launcher.tmp"
          mv "$launcher.tmp" "$launcher"
        fi
        exec "$launcher" "$@"
      '';
    };
  in {
    packages.${system} = {
      inherit android-cli;
      default = android-cli;
    };
    apps.${system}.android-cli = {
      type = "app";
      program = "${android-cli}/bin/android";
    };
    devShells.${system}.default = pkgs.mkShell {
      # android-cli first: the Nix SDK also ships the legacy `android` tool.
      packages = [ android-cli pkgs.jdk25 pkgs.python3 pkgs.imagemagick android.androidsdk ];
      JAVA_HOME = "${pkgs.jdk25}";
      ANDROID_HOME = sdk;
      ANDROID_SDK_ROOT = sdk;
      GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdk}/build-tools/37.0.0/aapt2";
    };
  };
}

# Demos

This directory contains the filebrowser demo app that demonstrates how to use AndroidX Media3 (ExoPlayer) for video playback.

## File Browser Demo

The filebrowser demo is a file browser and media player application. See [filebrowser/README.md](filebrowser/README.md) for more details.

## Running the demo

### From Android Studio

* File -> New -> Import Project -> Specify the root `media` folder.
* Choose `demo-filebrowser` from the run configuration dropdown list.
* Click Run.

### Using gradle from the command line:

* Open a Terminal window at the root `media` folder.
* Run `./gradlew :demo-filebrowser:assembleDebug` to build the app.
* Run `./gradlew :demo-filebrowser:installDebug` to install the app on a connected device.

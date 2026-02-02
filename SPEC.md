
- Android app with the following featuresShows on the screen the following information(in a scrollable container if not everything fits on a screen):
  - Show phone modell and android version
  - Availible camera resultions + fps in pairs to film with
  - The true fps of the camera. This information should be obtained be running the camera and looking at the exact hardware timestamps. Show the max, min, mean fps for the last 1min, 5min, 20min. This should use all the available data but only needs to be updated every second or so. 
  - Show a plot the clock drift of the system time (monotonic counter since boot) relative to the presice gnss time. I want a plot where on the x axis is the time since the app started and got its first gps timestamp. And on the y axis the diffrence to the gnss time. at this moment. This only needs to be updated every second or so.
- A makefile command to deploy the app to devices connected via usb
- A github action that creates a apk file on every push to main. 
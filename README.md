Alynx Live Wallpaper
====================

An Android app that allows you choose a video as wallpaper.
----------------------------------------------------------

[Homepage](https://livewallpaper.alynx.xyz/)

[![Play Store
Page](https://img.shields.io/badge/Play%20Store-Alynx%20Live%20Wallpaper-blue.svg?style=for-the-badge&logo=google-play)](https://play.google.com/store/apps/details?id=xyz.alynx.livewallpaper)

**Need GLESv2 or higher version support for cropping video size.**

# Notice

This is not a demo like most tutorial saying "build a android video wallpaper", which just create a MediaPlayer with WallpaperSurface as its destination.
MediaPlayer cannot crop video, instead it just fill screen with video frame, also it will hold audio channel.
I build this app with ExoPlayer because it can disable audio track, and write a custom OpenGL ES renderer to center-crop video, and added UI to choose video.
If you are learning create live wallpaper and getting in trouble with MediaPlayer, you can read my code for help.

# Icons

Some icons and videos are from following links, thanks to authors.

<div>Icons made by <a href="https://www.flaticon.com/authors/daniel-bruce" title="Daniel Bruce">Daniel Bruce</a> from <a href="https://www.flaticon.com/" title="Flaticon">www.flaticon.com</a> is licensed by <a href="http://creativecommons.org/licenses/by/3.0/" title="Creative Commons BY 3.0" target="_blank">CC 3.0 BY</a></div>

<div>Icons made by <a href="https://www.freepik.com/" title="Freepik">Freepik</a> from <a href="https://www.flaticon.com/" title="Flaticon">www.flaticon.com</a> is licensed by <a href="http://creativecommons.org/licenses/by/3.0/" title="Creative Commons BY 3.0" target="_blank">CC 3.0 BY</a></div>

<div>Icons made by <a href="https://www.flaticon.com/authors/hanan" title="Hanan">Hanan</a> from <a href="https://www.flaticon.com/" title="Flaticon">www.flaticon.com</a> is licensed by <a href="http://creativecommons.org/licenses/by/3.0/" title="Creative Commons BY 3.0" target="_blank">CC 3.0 BY</a></div>

<div>Video Fire Rain made by <a href="https://www.videvo.net/profile/arthur/" title="aRTHUR">aRTHUR</a>.

# License

Apache-2.0

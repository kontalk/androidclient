# Changelog

## [Next]
### Fixed
- Fix crash caused by database access from multiple threads (#1268)
- Fix crash when managing non-Kontalk users in a group (#1270)

## [4.3.0] - 2019-08-17
### Changed
- Group chat indicator in chat list (#818)
- Allow more control over search query
- Introduce more recent and performant SQLite library
- Support for Android Oreo 8.0 (#1215, #1228)
- Force use of foreground service only when absolutely necessary
- Support for multi-window (#1101)
- Improve registration screen (#1209)
- Allow replying to own messages
- Use same emoji type across all screens (#1229)
- Use an event bus for communicating with the message center (#1236)
- Update Smack library to version 4.3.1
- Generate QR codes in the background (#1237)
- Move some account-related preferences into their own new section
- Ask user to accept service terms if server requests it (#1211)
- Internal refactoring of registration process (#1241)

### Fixed
- Sort search results in inverted timestamp order (#1223)
- Faster reconnection on faulty networks
- Fix rare crash during PTT recording
- Fix message center not waking up when replying from notification
- Fix messages skipped until next app opening
- Fix message center not shutting down when inactive
- Fix invisible incoming replied messages (#1224)
- Remove unneeded permissions (#1238)
- Fix search items in wrong order (#1232)
- Fix crash on invoking camera app (#1231)
- Fix crash when sending debug log (#1239)
- Fix dangling download notification
- Fix crash when chatting with XMPP users (#1242)
- Fix OpenStreetMap static maps (#1251)
- Fix Google Maps static maps (#1254)
- Fix double upload of media (#1259)
- Fix database upgrade crash when upgrading from 4.1.6 (#1262)
- Fix message center crashing while in background (#1264)
- Picture notification for groups showing group title (#1230)

## [4.2.0] - 2018-09-04
### Changed
- Archived chats screen (#941)

### Fixed
- Fix out of memory issue with emojis (#1185)
- Fix crash on resuming location screen (#1199)
- Do not fix GPS on resume (#1202)
- Fix media messages not being sent (#1214)

## [4.2.0-beta4] - 2018-08-28
### Changed
- Massive refactoring of conversations list (#1206)
- Defer message center activities to the background (#1210)

### Fixed
- Fix crash during contacts sync (#1207)
- Fix crash in places search (#1208)

## [4.2.0-beta3] - 2018-08-21
### Fixed
- Fix push notifications not working

## [4.2.0-beta2] - 2018-08-21
### Changed
- Notification channels (aka notification categoris)
- Opt-out of server administrator messages (#1132)

### Fixed
- Fix messages stuck in sending state
- Fix push notifications not working

## [4.2.0-beta1] - 2018-08-06
### Changed
- Badge indicators for OEMs supporting it (#292)
- Migrate to Firebase Cloud Messaging (#779)
- Copy multiple messages to clipboard (#841)
- Upgrade to Smack 4.3 (#1058)
- Blind Trust Before Verification (#947)
- Accepted invitation is no longer required to chat (#947)
- More precise Google Maps location (#1197)

### Fixed
- Fix mixup with keys (#1127, #1171)
- Verify server key automatically (#1130)
- Fix group commands not processed (#1189)
- Fix crash in location view (#1991)
- Workaround Android bug 37032031 (#1192)
- Fix messages not marked as sent (#1193)
- Fix database deadlock (#1194)
- Ask for permission to detect user's phone number (#1201)

## [4.1.6] - 2018-06-24
### Fixed
- Temporarily revert to stable emoji version
- Fix public key retrieval
- Fix attachment panel close icon (#1184)

## [4.1.5] - 2018-06-19
### Changed
- Some major performance improvements (#1160)

### Fixed
- Fix rare crash on some devices (#1175, #1182, #1183)
- Fix several issues (#1167, #1169, 1170)

## [4.1.4] - 2018-03-18
### Changed
- Typing status in groups (#1152)

### Fixed
- Camera permission is needed also for external camera app (#1136)
- Allow HTTP connections for media uploads (#1137)
- Keep wake lock during message delivery and incoming processing (#1138)
- Crash with some balloon themes (#1142)
- Direct call requires explicit permission (#1144)
- Permission request loop with Google Maps provider (#1145)
- Crash with notifications on Android 4 (#1146)
- Crash requesting permission in contacts list (#1147)
- Use system-provided time on buggy firmwares (#1149)
- Request storage permission before importing personal key (#1153)
- Inapproriate use of plurals leading to crash (#1154)

## [4.1.3] - 2018-01-17
### Changed
- Image preview in notifications (#56)
- Play sound in open chats (#75)
- Support for Android 6 runtime permissions (#617)
- Direct reply for older Android versions (#1001)
- User name in group messages (#1122)

### Fixed
- Fix error exporting personal key (#1111)
- Fix crash for older Android versions (#1114)
- Fix emoji problem in tablets (#1115)
- Fix upload progress stuck at 100% (#1116)
- Fix CPU eating problem during download/upload (#1117)
- Fix wrong sender in contextual reply (#1118)
- Fix problem on crappy firmwares (#1119)
- Fix performance problem on very big address books (#1120)
- Fix QR codes inside viewport (#1131)

## [4.1.2] - 2017-12-06
### Changed
- Contextual reply (#594)

### Fixed
- Faster contacts sync (#1093)
- Fix crash during private key upload (#1102)
- Fix delay in delivery receipts (#1106)
- Workaround Cyanogenmod/LineageOS bug (#1112)

## [4.1.1] - 2017-11-13
### Changed
- Private key transfer (#122, #1090)
- Contact information screen (#456)
- New emoji set and keyboard (#814)
- Drop support for Android 2 (#1073)

### Fixed
- Crash when searching for places (#1091)
- Fix message delivery delay in some cases (#1094)

## [4.1.0] - 2017-11-01
### Changed
- Foreground service notification (#1084)
- Some performance improvements on messages operations

### Fixed
- Fix bug in smiley to emoji conversion (#1087)
- Fix serious bug in group members list status
- Show contact picture on Motorola Active Display

## [4.1.0-beta4] - 2017-10-12
### Changed
- Automatic conversion of text smileys into emojis (#940)

### Fixed
- Fix compatibility issue with Gajim (#1053)
- Fix rare crash (#1076)
- Fix Google location request loop (#1077)
- Show group name in search results (#1079)

## [4.1.0-beta3] - 2017-09-28
### Fixed
- Fix group creation issue (#1067)

## [4.1.0-beta2] - 2017-09-26
### Changed
- Support for plain HTTP download URLs (#1071)

### Fixed
- Workaround for Samsung devices not adhereing to battery optimization API (#1037)
- Fixed regression on status update (#1068)
- Allow use of location when precise location (GPS) is off (#1069)
- Fix inactive state request sent before authentication (#1070)

## [4.1.0-beta1] - 2017-09-10
### Changed
- Location sharing (#1004, #1027, #1062)
- Update to Smack 4.2.1 (#957)
- Autotrust (as in verification ignored) first key after an invitation (#866)
- Reuse connection object (#327, #956)
- Remember scrolling position (#853, #938)

### Fixed
- Fix memory leak (#1022, #1046)
- Proximity wake lock workaround for some devices (#1043)
- Downgrade Glide to version 3 for compatibility with Android 2 (#1047)
- Fix crash in direct share (#1050)
- Cleanup leftovers of abandoned groups (#1051)
- Use Bouncy Castle SHA-1 implementation (#1052)
- Fix crash on Android prior to Nougat (#1060, #1064)

## [4.0.4] - 2017-07-08
### Changed
- Reply directly from notification (#774)
- Improve notifications (#774)
- Receive any type of file (#697)
- Show max time of recording (#932)
- Image thumbnails respects original image ratio (#662)
- Show up to three line of preview for unread conversations (#888)
- Listen audio messages from phone speaker (#858)
- Direct share (#820)
- Support for Doze mode (#1030)

### Fixed
- Duck music when playing audio messages (#967)
- Handle no SIM card scenario (#998)
- Fix exchange images with other XMPP clients (#861)
- Workaround Android bug in encryption (#972)
- Fix clock sync issues (#813)
- Fix photo orientation issue (#927)
- Fix crash when deleting messages (#1005)
- Fix crash during search (#1011)

## [4.0.3] - 2017-04-22
### Fixed
- Fix crash on playing audio (#995)
- Fix rare crash (#996)

## [4.0.2] - 2017-04-22
### Fixed
- Fix crash on some devices (#994)

## [4.0.1] - 2017-04-17
### Changed
- Audio recording limit is now 2m on PTT and 5m on audio dialog
- Improve battery saving in offline mode (#869)
- Improve app navigation (#961)
- Fix group member information (#968)

### Fixed
- Fix audio files downloaded in wrong directory (#952)
- Fix crash on tablets (#953)
- Fix PTT crash after reaching time limit (#954)
- Fix registration error in rare cases (#963)
- Fix key never marked as trusted (#973)
- Fix upgrades from older versions (#983)
- Fix some crash issues (#955, #958, #965, #975, #976, #977)

## [4.0.0] - 2017-02-12
### Changed
- Groups: button for adding users again (for members losing their messages)

### Fixed
- Do not extract keyboard UI in landscape (#936)
- Fix floating action button animation (#937)
- Keep screen on while playing audio messages (#939)
- Fix crash on tablets (#943)

## [4.0.0-beta6.1] - 2017-01-23
### Fixed
- Fix email recipient for debug log (#934)
- Fix sharing pictures and text to Kontalk (#935)

## [4.0.0-beta6] - 2017-01-22
### Changed
- Toggle encryption per conversation (#271)
- Internal logging system (#623)

### Fixed
- Fix group chat issues (#874)
- Handle some Argentinian number special cases (#917)
- Request presence after accepting invitation (#924)
- Show subscription status in group members list (#928)
- Fix missing translations causing crash (#930)
- Fix crash in rare situations (#933)

## [4.0.0-beta5] - 2016-12-31
### Changed
- New 2017 icon!
- Better explain what happens when you delete a group thread (#898)
- Difference chat balloon themes for single and group chats (#906)
- Switch to Let's Encrypt certificates (#913)

### Fixed
- Fix profile picture disappearing in group chat members selection (#895)
- Fix wrong picture orientation on some devices (#899)
- Fix crash when sending square images (#903)
- Fix performance issue when composing messages (#904)
- Fix several stability issues (#901, #905, #908, #911)
- Fix several translation issues

## [4.0.0-beta4] - 2016-12-11
### Changed
- New 2017 icon!
- Allow multiple users selection in "add users to group" contexts (#892)
- Use different part message when owner leaves the group

### Fixed
- Allow add user to group only to owner (#890)
- Fix several fragment issues (#878)

## [4.0.0-beta3] - 2016-12-09
### Changed
- Create group: multiselect checkbox (by @acappelli, #886)
- Mark admin of a group (#876)
- Sticky conversations (#887)
- Group info screen: add users button (#875)

### Fixed
- Fix several group notifications and presentation bugs (#884)

## [4.0.0-beta2] - 2016-12-06
### Fixed
- Fix crash for some users (#883)

## [4.0.0-beta1] - 2016-12-04
### Changed
- Group chat (#179)
- Ask passphrase on first start if not available (#650)
- Improvement and clarification of some preferences (#749, #823)
- No need to input the phone number anymore when importing the key (#772)
- Preference to opt-out of Crashlytics (#827)
- User-initiated missed call verification (#829)

### Fixed
- Handle database errors (#46)
- Fix attachments panel crash (#699)
- Fix several tablet issues (#701, #793)
- Fix crash when blocking while offline (#723)
- Fix crash when clicking outside "Load more messages" button (#750)
- Fix search view bug (#752)
- Fix crash on missing external apps (#777, #782, #860)
- Fix user always being away (#787)
- Compress images and generate thumbnail in the background (#789)
- Fix compose activity not receiving correct lifecycle calls (#833)

## [3.1.10] - 2016-05-14
### Changed
- Update country flags (#731)

### Fixed
- Disable notification LED when requested (#722)
- Fix multiple photo upload (#727)
- Fix registration error in Persian locale (#728)
- Check for installed browser (#733)
- Fix crash sending photo from camera (#734)

## [3.1.9.1] - 2016-05-08
### Fixed
- Fix Huawei Protected Apps dialog (#720)

## [3.1.9] - 2016-05-08
### Changed
- Date stamp style improvements (#702)
- Composer bar style improvements (#713)
- Split preference sections into fragments (#719)

### Fixed
- Fight Huawei protected apps feature (#670)
- Fix several race conditions (#693, #694, #695, #698, #704, #708)
- Fix SSL/crypto issues (#685, #700, #706, #716)
- Fix non-latin locale SSL issue (#710)
- Fix several other crashes (#709, #711, #714, #715)

## [3.1.8] - 2016-04-23
### Changed
- Show message when search returns an empty result set (#647)
- New material icons (#689)

### Fixed
- Prevent app hanging during registration (#690)

## [3.1.7] - 2016-04-18
### Changed
- Update to latest emojis
- New file upload method

### Fixed
- Fix crash after donating through Google Play (#687)

## [3.1.6] - 2016-04-14
### Fixed
- Fix connection error (#676)
- Fix media upload/download (#677)
- Fix personal key export (#678)
- Fix crash on opening (#680)

## [3.1.5] - 2016-04-07
### Changed
- Automatic media download (#34)
- Increase play audio button in messages (#669)

### Fixed
- Fix message width issue in RTL languages (#653)
- Fix contact picture causing crash in older Android versions (#671)
- Fix blocking contact picture retrieval (#673)

## [3.1.4] - 2016-04-04
### Changed
- Sent images are now saved into Pictures/Kontalk/Sent (#539)
- Make personal key information copyable (#651)

### Fixed
- Fix outgoing images that could not be opened in gallery (#539)
- Fix tablet issue with text entry (#583)
- Fix available/away state (#637)
- Fix scroll-to-result when reloading in a search (#641)
- Fix possible crash during registration (#643)
- Fix presence handling (#644)
- Mark as read only notified conversation (#648)
- Correctly detect image rotation (#649)
- Fix truncated text in RTL languages (#653)
- Fix notification action not working (#660)
- Fix contact picture in Android 6 (#664)

## [3.1.3] - 2016-01-31
### Changed
- Search bar with new material search widget (#596)

### Fixed
- Fix regression on tablets (#632)
- Fix regression on search behaviour (#634)
- Fix crash on accepting chat invitation (#638)
- Fix crash on sending a picture in Android 6.0 (#640)

## [3.1.2] - 2016-01-24
### Added
- Preference for hiding blocked contacts (#384)
- Resend menu entry for failed messages (#606)
- Mark as read and call actions from notification (Android 4.1+) (#630)

### Changed
- Custom notification LED color preference (#299)
- Paged message list (1000 messages at a time) (#447)
- Export key trust preferences with personal key pack (#505)
- Export key in user-defined path (#610)
- Update to Smack 4.1.6 (#631)
- New attachment selection menu

### Fixed
- Jolla/Alien Dalvik compatibility (#616)
- Finally fix unexpected key change warning (#568)
- Fix duplicate entry in Android contacts (#592)
- Fix contact scrolling issues (#593)
- Fix adaptive ping manager (#597)
- Fix contact sorting issues (#601)
- Fix crash when sending multiple images (#607)
- Fix crash when sending media from another app (#625)

## [3.1.1] - 2015-11-09
### Added
- Offline indicator on main activity (#481)

### Changed
- Anticipate users update before completing registration (#572)
- Listen for contact changes in real time (#574)

### Fixed
- Fix own profile contact integration (#285)
- Unsubscribe from non-existing users (#464)
- Fix contact list showing hash values instead of actual name (#554)
- Fix unexpected key change warning (#568)
- Fix crash on Android 2.x (#570)
- Fix ringtone preference selection (#576)
- Fix possible crash on attachment selection (#582)
- Queue pending messages immediately after invitation is accepted (#588)
- Fix message loss in particular cases (#589)

## [3.1] - 2015-10-11
### Added
- New Hangout balloon theme with avatars (#551)

### Changed
- Material design (#412)
- Show message details on clicking a message (#557)

### Fixed
- Fix input field covered by warning bar (#542)
- Warn user when cancelling registration (#560)

## [3.0.6] - 2015-09-18
### Fixed
- This is an emergency bugfix release

## [3.0.5] - 2015-09-17
### Changed
- Verify message timestamp against digital signature (#201)
- New sliding pane layout interface (#543)

### Fixed
- Resume voice playback after rotation (#473)

## [3.0.4] - 2015-09-05
### Added
- Missing call based registration (#544)

### Fixed
- Fix timestamp inconsistencies (#513, #532)

## [3.0.3] - 2015-08-15
### Fixed
- Fix push-to-talk button issue (#515)
- Fix incompatibility issue with Android 2.2 (#528)
- Use correct status message when importing key (#529)
- Fix several crashes (#476, #526, #530, #535)

## [3.0.2] - 2015-06-25
### Changed
- Larger input field (#460)

### Fixed
- Don't hide keyboard if enter is configured as "send" (#451)
- Autoupdate contacts on conversation list (#484)
- Audio recording issues (#508, #515)
- Interrupt backoff if we connect to a new network (#507)
- Several crash fixes (#46, #485, #510, #511, #519)

## [3.0.1] - 2015-06-28
### Added
- New help link in settings for the wiki (#472)

### Changed
- Show when a user has been blocked by you (#239)
- Show when a contact is using an older version of the app (#469)

### Fixed
- Fix escaping of translated strings (#458)
- Fix crash during audio recording (#459)
- Keep device awake while recording audio (#474)

### Security
- Fix issues with encryption and signing (#417, #468)

## [3.0] - 2015-06-14
### Added
- Credits section (#199)

### Changed
- Switch to XMPP
- Warn user about registering another device (#338)
- Use name on public key when available (#424)
- Use a more meaningful name for attachments (#429)
- Request last seen on demand (#453)

### Fixed
- Do a login test when importing the key (#427)
- Fix file system location for attachments (#430)
- Fix wrong key warning when self chatting (#440)
- Fix bugs in push-to-talk message (#441, #442)
- Fix communication with unknown contacts (#446)
- Fix draft saving on incoming message (#448)
- Fix crash during sync (#454)

[Next]: https://github.com/kontalk/androidclient/compare/v4.2.0...HEAD
[4.3.0]: https://github.com/kontalk/androidclient/compare/v4.2.0...v4.3.0
[4.2.0]: https://github.com/kontalk/androidclient/compare/v4.2.0-beta4...v4.2.0
[4.2.0-beta4]: https://github.com/kontalk/androidclient/compare/v4.2.0-beta3...v4.2.0-beta4
[4.2.0-beta3]: https://github.com/kontalk/androidclient/compare/v4.2.0-beta2...v4.2.0-beta3
[4.2.0-beta2]: https://github.com/kontalk/androidclient/compare/v4.2.0-beta1...v4.2.0-beta2
[4.2.0-beta1]: https://github.com/kontalk/androidclient/compare/v4.1.6...v4.2.0-beta1
[4.1.6]: https://github.com/kontalk/androidclient/compare/v4.1.5...v4.1.6
[4.1.5]: https://github.com/kontalk/androidclient/compare/v4.1.4...v4.1.5
[4.1.4]: https://github.com/kontalk/androidclient/compare/v4.1.3...v4.1.4
[4.1.3]: https://github.com/kontalk/androidclient/compare/v4.1.2...v4.1.3
[4.1.2]: https://github.com/kontalk/androidclient/compare/v4.1.1...v4.1.2
[4.1.1]: https://github.com/kontalk/androidclient/compare/v4.1.0...v4.1.1
[4.1.0]: https://github.com/kontalk/androidclient/compare/v4.1.0-beta4...v4.1.0
[4.1.0-beta4]: https://github.com/kontalk/androidclient/compare/v4.1.0-beta3...v4.1.0-beta4
[4.1.0-beta3]: https://github.com/kontalk/androidclient/compare/v4.1.0-beta2...v4.1.0-beta3
[4.1.0-beta2]: https://github.com/kontalk/androidclient/compare/v4.1.0-beta1...v4.1.0-beta2
[4.1.0-beta1]: https://github.com/kontalk/androidclient/compare/v4.0.4...v4.1.0-beta1
[4.0.4]: https://github.com/kontalk/androidclient/compare/v4.0.3...v4.0.4
[4.0.3]: https://github.com/kontalk/androidclient/compare/v4.0.2...v4.0.3
[4.0.2]: https://github.com/kontalk/androidclient/compare/v4.0.1...v4.0.2
[4.0.1]: https://github.com/kontalk/androidclient/compare/v4.0.0...v4.0.1
[4.0.0]: https://github.com/kontalk/androidclient/compare/v4.0.0-beta6.1...v4.0.0
[4.0.0-beta6.1]: https://github.com/kontalk/androidclient/compare/v4.0.0-beta6...v4.0.0-beta6.1
[4.0.0-beta6]: https://github.com/kontalk/androidclient/compare/v4.0.0-beta5...v4.0.0-beta6
[4.0.0-beta5]: https://github.com/kontalk/androidclient/compare/v4.0.0-beta4...v4.0.0-beta5
[4.0.0-beta4]: https://github.com/kontalk/androidclient/compare/v4.0.0-beta3...v4.0.0-beta4
[4.0.0-beta3]: https://github.com/kontalk/androidclient/compare/v4.0.0-beta2...v4.0.0-beta3
[4.0.0-beta2]: https://github.com/kontalk/androidclient/compare/v4.0.0-beta1...v4.0.0-beta2
[4.0.0-beta1]: https://github.com/kontalk/androidclient/compare/v3.1.10...v4.0.0-beta1
[3.1.10]: https://github.com/kontalk/androidclient/compare/v3.1.9.1...v3.1.10
[3.1.9.1]: https://github.com/kontalk/androidclient/compare/v3.1.9...v3.1.9.1
[3.1.9]: https://github.com/kontalk/androidclient/compare/v3.1.8...v3.1.9
[3.1.8]: https://github.com/kontalk/androidclient/compare/v3.1.7...v3.1.8
[3.1.7]: https://github.com/kontalk/androidclient/compare/v3.1.6...v3.1.7
[3.1.6]: https://github.com/kontalk/androidclient/compare/v3.1.5...v3.1.6
[3.1.5]: https://github.com/kontalk/androidclient/compare/v3.1.4...v3.1.5
[3.1.4]: https://github.com/kontalk/androidclient/compare/v3.1.3...v3.1.4
[3.1.3]: https://github.com/kontalk/androidclient/compare/v3.1.2...v3.1.3
[3.1.2]: https://github.com/kontalk/androidclient/compare/v3.1.1...v3.1.2
[3.1.1]: https://github.com/kontalk/androidclient/compare/v3.1...v3.1.1
[3.1]: https://github.com/kontalk/androidclient/compare/v3.1-beta3...v3.1
[3.1-beta3]: https://github.com/kontalk/androidclient/compare/v3.0.6...v3.1-beta3
[3.0.6]: https://github.com/kontalk/androidclient/compare/v3.0.5...v3.0.6
[3.0.5]: https://github.com/kontalk/androidclient/compare/v3.0.4...v3.0.5
[3.0.4]: https://github.com/kontalk/androidclient/compare/v3.1-beta2...v3.0.4
[3.1-beta2]: https://github.com/kontalk/androidclient/compare/v3.1-beta1...v3.1-beta2
[3.1-beta1]: https://github.com/kontalk/androidclient/compare/v3.0.3...v3.1-beta1
[3.0.3]: https://github.com/kontalk/androidclient/compare/v3.0.2...v3.0.3
[3.0.2]: https://github.com/kontalk/androidclient/compare/v3.0.1...v3.0.2
[3.0.1]: https://github.com/kontalk/androidclient/compare/v3.0...v3.0.1
[3.0]: https://github.com/kontalk/androidclient/compare/v3.0-rc4...v3.0

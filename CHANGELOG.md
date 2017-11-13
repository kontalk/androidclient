# Change Log

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

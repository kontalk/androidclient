# Change Log

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

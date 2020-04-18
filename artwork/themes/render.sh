#!/bin/bash
# Renders all balloons vector images directly into app resources
# Requires rsvg-convert

set -e

ZOOM_MDPI=1
ZOOM_HDPI=2
ZOOM_XHDPI=3
ZOOM_XXHDPI=4
ZOOM_XXXHDPI=5

render() {
  rsvg-convert -z "$4" "$1" -o "../../app/src/main/res/drawable-$2/$3"
}

render_all() {
  render "$1" "mdpi" "$2" ${ZOOM_MDPI}
  render "$1" "hdpi" "$2" ${ZOOM_HDPI}
  render "$1" "xhdpi" "$2" ${ZOOM_XHDPI}
  render "$1" "xxhdpi" "$2" ${ZOOM_XXHDPI}
  render "$1" "xxxhdpi" "$2" ${ZOOM_XXXHDPI}
}

render_night_all() {
  render "$1" "night-mdpi" "$2" ${ZOOM_MDPI}
  render "$1" "night-hdpi" "$2" ${ZOOM_HDPI}
  render "$1" "night-xhdpi" "$2" ${ZOOM_XHDPI}
  render "$1" "night-xxhdpi" "$2" ${ZOOM_XXHDPI}
  render "$1" "night-xxxhdpi" "$2" ${ZOOM_XXXHDPI}
}

render_all balloon_hangout_outgoing.9.svg balloon_hangout_outgoing.9.png
render_all balloon_hangout_block_outgoing.9.svg balloon_hangout_block_outgoing.9.png
render_all balloon_hangout_incoming.9.svg balloon_hangout_incoming.9.png
render_all balloon_hangout_block_incoming.9.svg balloon_hangout_block_incoming.9.png
# TODO other themes

render_night_all balloon_hangout_block_outgoing_dark.9.svg balloon_hangout_block_outgoing.9.png
render_night_all balloon_hangout_outgoing_dark.9.svg balloon_hangout_outgoing.9.png
render_night_all balloon_hangout_block_incoming_dark.9.svg balloon_hangout_block_incoming.9.png
render_night_all balloon_hangout_incoming_dark.9.svg balloon_hangout_incoming.9.png
# TODO other themes

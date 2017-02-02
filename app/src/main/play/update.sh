#!/bin/bash
# Copy JSON-based store listing into data files for gradle-play-publisher
# Locales with no short or full description will be skipped.
# Licensed under Public Domain

set -e

DEFAULT_LOCALE="en-US"

function check_lang() {
    INFILE="$1"
    LOCALE="$2"
    SHORT_DESC=$(cat ${INFILE} | perl -MJSON -Mutf8 -n0777 \
     -E 'binmode(STDIN, ":encoding(UTF-8)"); binmode(STDOUT, ":encoding(UTF-8)"); $r = decode_json($_); print $r->{short_description}' -)
    FULL_DESC=$(cat ${INFILE} | perl -MJSON -Mutf8 -n0777 \
     -E 'binmode(STDIN, ":encoding(UTF-8)"); binmode(STDOUT, ":encoding(UTF-8)"); $r = decode_json($_); print $r->{full_description}' -)
    SHORT_DESC_LEN=$(expr length "${SHORT_DESC}")
    [ -n "${SHORT_DESC}" ] && [ "${SHORT_DESC_LEN}" -le "80" ] && [ -n "${FULL_DESC}" ]
}

function copy_lang() {
    INFILE="$1"
    LOCALE="$2"
    echo "Updating locale ${LOCALE}"
    mkdir -p ${LOCALE}/listing
    cat ${INFILE} | perl -MJSON -Mutf8 -n0777 \
     -E 'binmode(STDIN, ":encoding(UTF-8)"); binmode(STDOUT, ":encoding(UTF-8)"); $r = decode_json($_); say $r->{title}' - \
     >${LOCALE}/listing/title
    cat ${INFILE} | perl -MJSON -Mutf8 -n0777 \
     -E 'binmode(STDIN, ":encoding(UTF-8)"); binmode(STDOUT, ":encoding(UTF-8)"); $r = decode_json($_); say $r->{short_description}' - \
     >${LOCALE}/listing/shortdescription
    cat ${INFILE} | perl -MJSON -Mutf8 -n0777 \
     -E 'binmode(STDIN, ":encoding(UTF-8)"); binmode(STDOUT, ":encoding(UTF-8)"); $r = decode_json($_); say $r->{full_description}' - \
     >${LOCALE}/listing/fulldescription
    cat ${INFILE} | perl -MJSON -Mutf8 -n0777 \
     -E 'binmode(STDIN, ":encoding(UTF-8)"); binmode(STDOUT, ":encoding(UTF-8)"); $r = decode_json($_); say $r->{recent_changes}' - \
     >${LOCALE}/whatsnew
}

copy_lang "../../../../dist/google_play.json" ${DEFAULT_LOCALE}

for IN in ../../../../dist/google_play_*.json;
do
    LOCALE=$(basename ${IN} .json | sed 's/google_play_//')
    check_lang ${IN} ${LOCALE} && copy_lang ${IN} ${LOCALE} ||
        echo "Skipping locale ${LOCALE}"
done

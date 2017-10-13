#!/bin/bash
# Copy JSON-based store listing into data files for gradle-play-publisher
# Locales with no short or full description will be skipped.
# Licensed under Public Domain

set -e

DEFAULT_LOCALE="en-US"
MAX_TITLE_LEN=30
MAX_SHORT_DESC_LEN=80
MAX_FULL_DESC_LEN=4000
MAX_WHATSNEW_LEN=500

function extract_json() {
    INFILE="$1"
    KEY="$2"
    cat ${INFILE} | perl -MJSON -Mutf8 -n0777 \
     -E "binmode(STDIN, \":encoding(UTF-8)\"); binmode(STDOUT, \":encoding(UTF-8)\"); \$r = decode_json(\$_); print \$r->{${KEY}}" -
}

function check_lang() {
    INFILE="$1"
    LOCALE="$2"
    SHORT_DESC=$(cat ${INFILE} | perl -MJSON -Mutf8 -n0777 \
     -E 'binmode(STDIN, ":encoding(UTF-8)"); binmode(STDOUT, ":encoding(UTF-8)"); $r = decode_json($_); print $r->{short_description}' -)
    FULL_DESC=$(cat ${INFILE} | perl -MJSON -Mutf8 -n0777 \
     -E 'binmode(STDIN, ":encoding(UTF-8)"); binmode(STDOUT, ":encoding(UTF-8)"); $r = decode_json($_); print $r->{full_description}' -)
    SHORT_DESC_LEN=$(expr length "${SHORT_DESC}")
    [ -n "${SHORT_DESC}" ] && [ "${SHORT_DESC_LEN}" -le "${MAX_SHORT_DESC_LEN}" ] && [ -n "${FULL_DESC}" ]
}

function extract_check() {
    INFILE="$1"
    KEY="$2"
    MAX_LEN="$3"

    OUT=$(extract_json ${INFILE} ${KEY})
    OUT_LEN=$(expr length "${OUT}")
    [ -n "${OUT}" ] && [ "${OUT_LEN}" -le "${MAX_LEN}" ] && echo "${OUT}"
}

function copy_lang() {
    INFILE="$1"
    LOCALE="$2"

    TITLE=$(extract_check ${INFILE} "title" ${MAX_TITLE_LEN})
    SHORT_DESC=$(extract_check ${INFILE} "short_description" ${MAX_SHORT_DESC_LEN})
    FULL_DESC=$(extract_check ${INFILE} "full_description" ${MAX_FULL_DESC_LEN})
    WHATSNEW=$(extract_check ${INFILE} "recent_changes" ${MAX_WHATSNEW_LEN})

    if [ -n "${SHORT_DESC}" ] && [ -n "${FULL_DESC}" ]; then
        echo "Updating locale ${LOCALE}"
        mkdir -p ${LOCALE}/listing
        [ -n "${TITLE}" ] && echo -n "${TITLE}" >${LOCALE}/listing/title
        echo -n "${SHORT_DESC}" >${LOCALE}/listing/shortdescription
        echo -n "${FULL_DESC}" >${LOCALE}/listing/fulldescription
        [ -n "${WHATSNEW}" ] && echo -n "${WHATSNEW}" >${LOCALE}/whatsnew
    else
        echo "Skipping locale ${LOCALE}"
    fi
}

copy_lang "../../../../dist/google_play.json" ${DEFAULT_LOCALE}

for IN in ../../../../dist/google_play_*.json;
do
    LOCALE=$(basename ${IN} .json | sed 's/google_play_//')
    check_lang ${IN} ${LOCALE} && copy_lang ${IN} ${LOCALE} ||
        echo "Skipping locale ${LOCALE}"
done

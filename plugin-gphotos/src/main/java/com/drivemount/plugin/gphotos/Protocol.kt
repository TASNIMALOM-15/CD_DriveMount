package com.drivemount.plugin.gphotos

/** DriveMount ↔ প্লাগইন Messenger প্রোটোকল (DriveMount-এও এই একই মান আছে) */
object Protocol {
    const val VERSION = 1

    const val MSG_HELLO = 1          // → MSG_HELLO_REPLY
    const val MSG_HELLO_REPLY = 2    // data: protocol, versionName, stage, signedIn
    const val MSG_TEST_UPLOAD = 3    // নকল আপলোড → MSG_PROGRESS... → MSG_DONE
    const val MSG_PROGRESS = 4       // data: percent, text
    const val MSG_DONE = 5           // data: ok, message
}

package com.sundown.player

import android.app.Application

/**
 * Nothing exotic here on purpose: Room, DataStore and the Media3 session are
 * created lazily by the pieces that own them, so startup does no blocking work.
 */
class SundownApp : Application()

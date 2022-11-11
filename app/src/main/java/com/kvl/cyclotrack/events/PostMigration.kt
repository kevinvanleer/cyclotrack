package com.kvl.cyclotrack.events

data class PostMigration(val oldVersion: Int, val newVersion: Int)

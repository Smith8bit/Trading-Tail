package com.tradingtail.common

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A stable, globally-unique id for an execution — its identity across devices (the local Room id isn't). */
@OptIn(ExperimentalUuidApi::class)
fun randomSyncId(): String = Uuid.random().toString()

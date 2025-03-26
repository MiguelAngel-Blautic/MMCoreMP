package com.blautic.mmcore.Camera

import Entity.DetectedObject
import Entity.Objeto
import Entity.Person

interface ImageDetectorCallback {
    fun onResultsPersons(resultList: List<Person>)
    fun onResultsObject(result: List<Objeto>)
    fun onError(error: String, errorCode: Int)
}
package com.blautic.mmcore.Camera

import Entity.ObjetLabel

abstract class ImageDetector (val callback: ImageDetectorCallback){
    var objectLabels: MutableList<ObjetLabel> = mutableListOf()
    var imageRotation: Int = 0

    fun setObjetsLabels(lista: List<ObjetLabel>){
        this.objectLabels = lista.toMutableList()
    }
    fun addObjetLabel(objeto: ObjetLabel){
        if(!objectLabels.contains(objeto)){
            objectLabels.add(objeto)
        }
    }

    abstract fun detectLiveStreamImage(bitmap: Any, rotation: Float)
    abstract fun clearObjectDetector()
    abstract fun clearPoseDetector()
    abstract fun setupObjectDetector()
    abstract fun setupPoseDetector()
    fun clearDetector(){
        clearObjectDetector()
        clearPoseDetector()
    }
    fun setupDetector(){
        setupObjectDetector()
        setupPoseDetector()
    }
}

expect class ImageDetectorFactory {
    companion object {
        fun create(context: Any?, callback: ImageDetectorCallback): ImageDetector
    }
}
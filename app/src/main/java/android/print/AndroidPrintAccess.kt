package android.print

/**
 * `PrintDocumentAdapter.LayoutResultCallback` and `WriteResultCallback` have a package-private
 * no-arg constructor in the Android SDK, so app code (in package com.example) cannot subclass
 * them directly. Declaring these thin pass-through subclasses inside the same package name
 * (android.print) grants access to that constructor, which app code can then extend normally.
 */
abstract class AccessibleLayoutResultCallback : PrintDocumentAdapter.LayoutResultCallback()

abstract class AccessibleWriteResultCallback : PrintDocumentAdapter.WriteResultCallback()

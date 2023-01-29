/*
 * The contents of this file are subject to the Common Public Attribution License Version 1.0.
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * https://github.com/Slion/Fulguris/blob/main/LICENSE.CPAL-1.0.
 * The License is based on the Mozilla Public License Version 1.1, but Sections 14 and 15 have been
 * added to cover use of software over a computer network and provide for limited attribution for
 * the Original Developer. In addition, Exhibit A has been modified to be consistent with Exhibit B.
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF
 * ANY KIND, either express or implied. See the License for the specific language governing rights
 * and limitations under the License.
 *
 * The Original Code is Fulguris.
 *
 * The Original Developer is the Initial Developer.
 * The Initial Developer of the Original Code is Stéphane Lenclud.
 *
 * All portions of the code written by Stéphane Lenclud are Copyright © 2020 Stéphane Lenclud.
 * All Rights Reserved.
 */

package styx

import androidx.lifecycle.DefaultLifecycleObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Styx component
 */
abstract class Component : DefaultLifecycleObserver  /*: androidx.lifecycle.ViewModel()*/ {

    // Setup an async scope on the main/UI thread dispatcher.
    // This one as opposed to viwModelScope will not be cancelled therefore all operation will complete before the process quits.
    // Use this if you want to manipulate views and other object bound to the UI thread.
    val iScopeMainThread = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Only use this for operations that do not need the UI thread as they will run on another thread.
    // Typically used for file write or read operations.
    val iScopeThreadPool = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
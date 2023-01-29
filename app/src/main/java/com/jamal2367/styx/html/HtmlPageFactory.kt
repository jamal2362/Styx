/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.html

import io.reactivex.Single

/**
 * A factory that builds an HTML page.
 */
interface HtmlPageFactory {

    /**
     * Build the HTML page and emit the URL.
     */
    fun buildPage(): Single<String>

}

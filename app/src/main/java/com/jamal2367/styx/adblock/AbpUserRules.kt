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

package com.jamal2367.styx.adblock

import android.net.Uri
import com.jamal2367.styx.database.adblock.UserRulesRepository
import jp.hazuki.yuzubrowser.adblock.core.ContentRequest
import jp.hazuki.yuzubrowser.adblock.filter.unified.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/*
    user rules:
    implements what is called 'dynamic filtering' in ubo: https://github.com/gorhill/uBlock/wiki/Dynamic-filtering:-quick-guide
    uses only 2 types of filters and a special filter container working with pageUrl.host as tag (empty for global rules)
    each rule is a single filter! this is important for easy display of existing filter rules
     -> TODO: test how much slower it is compared to optimized filters
          e.g. global block of example.net and example.com could be one filter with both domains in the domainMap
          but when adding/removing rules, it would be necessary to remove the filter, and add a new one with modified domainMap
          definitely possible, but is it worth the work?
          also content types could be joined
          first tests: no problem if all filters use different tags
          -> try using a lot of global filters (empty tag), each with some domain
            this is not yet implemented, but will be used for uBo style dynamic filtering
    TODO: improve loading speed
     slow load speed is why yuzu uses storage in files instead of DB (current db is 10 times slower!)
     loading (in background) takes some 100 ms for few entries, 1.7 s for 2400 entries (S4 mini)
       ca half time if not loading at the same time as ad block lists
     -> it's not horrible, but still needs to be improved
      first step: check which part is slow -> loading from db takes twice as long as creating the filters, adding to UserFilterContainer is (currently) negligible
 */

@Singleton
class AbpUserRules @Inject constructor(
    private val userRulesRepository: UserRulesRepository,
) {

    private val userRules by lazy {
        UserFilterContainer().also {
            userRulesRepository.getAllRules().forEach(it::add)
        }
    }
//    private lateinit var userRules: UserFilterContainer
//    private val userRules = UserFilterContainer()

    // true: block
    // false: allow
    // null: nothing (relevant to supersede more general block/allow rules)
    fun getResponse(contentRequest: ContentRequest): Boolean? {
        return userRules.get(contentRequest)?.response
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun addUserRule(filter: UnifiedFilterResponse) {
        userRules.add(filter)
        GlobalScope.launch(Dispatchers.IO) { userRulesRepository.addRules(listOf(filter)) }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun removeUserRule(filter: UnifiedFilterResponse) {
        userRules.remove(filter)
        GlobalScope.launch(Dispatchers.IO) { userRulesRepository.removeRule(filter) }
    }

/*
        some examples
        entire page: <page domain>, "", ContentRequest.TYPE_ALL, false
        everything from youtube.com: "", "youtube.com", ContentRequest.TYPE_ALL, false
        everything 3rd party from youtube.com: "", "youtube.com", ContentRequest.TYPE_ALL, true
        all 3rd party frames: "", "", ContentRequest.TYPE_SUB_DOCUMENT, true //TODO: SHOULD be sub_document, but not checked

        find content types in ContentRequest, and how to get it from request in AdBlock -> WebResourceRequest.getContentType
 */

    // domains as returned by url.host -> should be valid, and not contains htto(s)
    private fun createUserFilter(
        pageDomain: String,
        requestDomain: String,
        contentType: Int,
        thirdParty: Boolean,
    ): UnifiedFilter {
        // 'domains' contains (usually 3rd party) domains, but can also be same as requestDomain (or subdomain of requestDomain)
        // include is always set to true (filter valid only on this domain, and any subdomain if there is no more specific rule)
        val domains = if (pageDomain.isNotEmpty())
            SingleDomainMap(true, pageDomain)
        else null

        // thirdParty true means filter only applied to 3rd party content, translates to 1 in the filter
        //  0 would be only first party, -1 is for both
        //  maybe implement 0 as well, but I think it's not used in uBo (why would i want to block 1st, but not 3rd party stuff?)
        val thirdPartyInt = if (thirdParty) 1 else -1

        // HostFilter for specific request domain, ContainsFilter with empty pattern otherwise
        return if (requestDomain.isEmpty())
            ContainsFilter(requestDomain, contentType, true, domains, thirdPartyInt)
        else
            HostFilter(requestDomain, contentType, domains, thirdPartyInt)
    }

    @Suppress("SameParameterValue")
    private fun addUserRule(
        pageDomain: String,
        requestDomain: String,
        contentType: Int,
        thirdParty: Boolean,
        response: Boolean?,
    ) {
        addUserRule(UnifiedFilterResponse(createUserFilter(pageDomain,
            requestDomain,
            contentType,
            thirdParty), response))
    }

    @Suppress("SameParameterValue")
    private fun removeUserRule(
        pageDomain: String,
        requestDomain: String,
        contentType: Int,
        thirdParty: Boolean,
        response: Boolean?,
    ) {
        removeUserRule(UnifiedFilterResponse(createUserFilter(pageDomain,
            requestDomain,
            contentType,
            thirdParty), response))
    }

    fun isAllowed(pageUrl: Uri): Boolean {
        // TODO: checking by using a fake request might be "slower than necessary"? but sure is faster a than DB query
        //  anyway, this needs to be changed once there can be more rules for a page
        return userRules.get(ContentRequest(pageUrl,
            pageUrl.host,
            ContentRequest.TYPE_ALL,
            FIRST_PARTY,
            tags = listOf("")))?.response == false
    }

    fun allowPage(pageUrl: Uri, add: Boolean) {
        val domain = pageUrl.host ?: return
        if (add)
            addUserRule(domain, "", ContentRequest.TYPE_ALL, thirdParty = false, response = false)
        else
            removeUserRule(domain,
                "",
                ContentRequest.TYPE_ALL,
                thirdParty = false,
                response = false)
    }

}

const val USER_BLOCKED = "user"

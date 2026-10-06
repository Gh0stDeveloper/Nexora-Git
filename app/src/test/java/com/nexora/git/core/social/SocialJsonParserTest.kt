package com.nexora.git.core.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialJsonParserTest {

    private val parser = SocialJsonParser()

    @Test
    fun parsesProfileOrganizationsUsersAndStars() {
        val profile = parser.profile(
            """
            {
              "id":1,
              "node_id":"U_1",
              "login":"ghost",
              "name":"Ghost Developer",
              "avatar_url":"https://example/avatar",
              "html_url":"https://github.com/ghost",
              "bio":"Android developer",
              "company":"Nexora",
              "location":"Mexico",
              "blog":"https://example.dev",
              "email":"ghost@example.dev",
              "twitter_username":"ghostdev",
              "hireable":true,
              "public_repos":20,
              "public_gists":2,
              "followers":100,
              "following":30,
              "created_at":"now",
              "updated_at":"later"
            }
            """.trimIndent(),
        )

        val organizations = parser.organizations(
            """
            [
              {
                "id":2,
                "node_id":"O_2",
                "login":"Nexora",
                "avatar_url":"https://example/org",
                "description":"Organization",
                "html_url":"https://github.com/Nexora"
              }
            ]
            """.trimIndent(),
        )

        val users = parser.users(
            """
            [
              {
                "id":3,
                "node_id":"U_3",
                "login":"friend",
                "avatar_url":"https://example/friend",
                "html_url":"https://github.com/friend",
                "type":"User"
              }
            ]
            """.trimIndent(),
        )

        val starred = parser.starredRepositories(
            """
            [
              {
                "id":4,
                "node_id":"R_4",
                "name":"repo",
                "full_name":"friend/repo",
                "private":false,
                "description":"Repository",
                "html_url":"https://github.com/friend/repo",
                "language":"Kotlin",
                "stargazers_count":15,
                "forks_count":3,
                "updated_at":"later",
                "owner":{
                  "login":"friend",
                  "avatar_url":"https://example/friend"
                }
              }
            ]
            """.trimIndent(),
        )

        assertEquals("ghost", profile.login)
        assertEquals(100, profile.followers)
        assertTrue(profile.hireable == true)
        assertEquals("Nexora", organizations.single().login)
        assertEquals("friend", users.single().login)
        assertEquals("friend/repo", starred.single().fullName)
        assertEquals(15L, starred.single().stars)
    }

    @Test
    fun parsesActivityNotificationsAndSubscription() {
        val events = parser.activity(
            """
            [
              {
                "id":"10",
                "type":"IssuesEvent",
                "actor":{
                  "login":"ghost",
                  "avatar_url":"https://example/avatar"
                },
                "repo":{"name":"ghost/repo"},
                "public":true,
                "created_at":"now",
                "payload":{
                  "action":"opened",
                  "issue":{
                    "number":12,
                    "title":"Bug"
                  }
                }
              }
            ]
            """.trimIndent(),
        )

        val notifications = parser.notifications(
            """
            [
              {
                "id":"100",
                "unread":true,
                "reason":"mention",
                "updated_at":"now",
                "last_read_at":null,
                "subject":{
                  "title":"Review requested",
                  "url":"https://api.github.com/subject",
                  "latest_comment_url":null,
                  "type":"PullRequest"
                },
                "repository":{
                  "full_name":"ghost/repo",
                  "html_url":"https://github.com/ghost/repo"
                }
              }
            ]
            """.trimIndent(),
        )

        val subscription = parser.subscription(
            """
            {
              "subscribed":true,
              "ignored":false,
              "reason":"subscribed",
              "created_at":"now"
            }
            """.trimIndent(),
        )

        assertEquals("opened", events.single().action)
        assertEquals(12, events.single().number)
        assertEquals("Bug", events.single().title)
        assertTrue(notifications.single().unread)
        assertEquals(
            "ghost/repo",
            notifications.single().repositoryFullName,
        )
        assertTrue(subscription.subscribed)
        assertFalse(subscription.ignored)
    }
}

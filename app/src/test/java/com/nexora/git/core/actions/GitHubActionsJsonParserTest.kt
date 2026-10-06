package com.nexora.git.core.actions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubActionsJsonParserTest {

    private val parser = GitHubActionsJsonParser()

    @Test
    fun parsesWorkflowsRunsJobsStepsAndArtifacts() {
        val workflows = parser.workflows(
            """
            {
              "total_count":1,
              "workflows":[
                {
                  "id":10,
                  "node_id":"W_10",
                  "name":"Android CI",
                  "path":".github/workflows/android-ci.yml",
                  "state":"active",
                  "created_at":"now",
                  "updated_at":"later",
                  "html_url":"https://github.com/example/repo/actions/workflows/android-ci.yml",
                  "badge_url":"https://example/badge"
                }
              ]
            }
            """.trimIndent(),
        )

        val runs = parser.runs(
            """
            {
              "total_count":1,
              "workflow_runs":[
                {
                  "id":20,
                  "node_id":"R_20",
                  "name":"Android CI",
                  "display_title":"Build app",
                  "event":"push",
                  "status":"completed",
                  "conclusion":"success",
                  "workflow_id":10,
                  "run_number":5,
                  "run_attempt":2,
                  "head_branch":"main",
                  "head_sha":"abcdef123456",
                  "html_url":"https://example/run",
                  "created_at":"now",
                  "updated_at":"later",
                  "run_started_at":"start",
                  "actor":{"login":"ghost"}
                }
              ]
            }
            """.trimIndent(),
        )

        val jobs = parser.jobs(
            """
            {
              "total_count":1,
              "jobs":[
                {
                  "id":30,
                  "run_id":20,
                  "run_attempt":2,
                  "node_id":"J_30",
                  "name":"Build",
                  "status":"completed",
                  "conclusion":"success",
                  "started_at":"start",
                  "completed_at":"end",
                  "html_url":"https://example/job",
                  "runner_name":"runner",
                  "runner_group_name":"GitHub Actions",
                  "labels":["ubuntu-latest"],
                  "steps":[
                    {
                      "name":"Checkout",
                      "status":"completed",
                      "conclusion":"success",
                      "number":1,
                      "started_at":"start",
                      "completed_at":"end"
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        val artifacts = parser.artifacts(
            """
            {
              "total_count":1,
              "artifacts":[
                {
                  "id":40,
                  "node_id":"A_40",
                  "name":"app-debug",
                  "size_in_bytes":1024,
                  "expired":false,
                  "created_at":"now",
                  "expires_at":"future",
                  "updated_at":"later",
                  "workflow_run":{"id":20}
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("Android CI", workflows.single().name)
        assertEquals("success", runs.single().conclusion)
        assertEquals("Checkout", jobs.single().steps.single().name)
        assertEquals("app-debug", artifacts.single().name)
        assertFalse(artifacts.single().expired)
    }

    @Test
    fun parsesSingleRun() {
        val run = parser.run(
            """
            {
              "id":20,
              "node_id":"R_20",
              "name":"Android CI",
              "display_title":"Build app",
              "event":"workflow_dispatch",
              "status":"in_progress",
              "conclusion":null,
              "workflow_id":10,
              "run_number":6,
              "run_attempt":1,
              "head_branch":"feature",
              "head_sha":"abcdef123456",
              "html_url":"https://example/run",
              "created_at":"now",
              "updated_at":"later",
              "run_started_at":"start",
              "actor":{"login":"ghost"}
            }
            """.trimIndent(),
        )

        assertEquals(20L, run.id)
        assertEquals("workflow_dispatch", run.event)
        assertTrue(run.conclusion == null)
    }
}

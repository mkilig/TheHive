package org.thp.thehive.services

import play.api.libs.json.{JsNumber, JsString, JsTrue, JsValue}
import play.api.test.PlaySpecification

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._

class CaseTemplateSrvTest extends PlaySpecification with TestAppBuilder {
  implicit val authcontext: AuthContext = DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert").authContext

  "case template service" should {
    "create a case template" in testApp { app =>
      app[Database].tryTransaction { implicit graph =>
        app[CaseTemplateSrv].create(
          caseTemplate = CaseTemplate(
            name = "case template test 1",
            displayName = "case template test 1",
            titlePrefix = Some("[CTT]"),
            description = Some("description ctt1"),
            severity = Some(2),
            flag = false,
            tlp = Some(1),
            pap = Some(3),
            summary = Some("summary case template test 1")
          ),
          organisation = app[OrganisationSrv].getOrFail("cert").get,
          tagNames = Set("""testNamespace.testPredicate="t2"""", """testNamespace.testPredicate="newOne""""),
          tasks = Seq(
            (
              Task("task case template case template test 1", "group1", None, TaskStatus.Waiting, flag = false, None, None, 0, None),
              app[UserSrv].get("certuser@thehive.local").headOption()
            )
          ),
          customFields = Seq(("string1", Some("love")), ("boolean1", Some(false)))
        )
      } must beASuccessfulTry

      app[Database].roTransaction { implicit graph =>
        app[TagSrv].initSteps.getByName("testNamespace", "testPredicate", Some("newOne")).exists() must beTrue
        app[TaskSrv].initSteps.has("title", "task case template case template test 1").exists() must beTrue
        val richCT = app[CaseTemplateSrv].initSteps.has("name", "case template test 1").richCaseTemplate.getOrFail().get
        richCT.customFields.length shouldEqual 2
      }
    }

    "add a task to a template" in testApp { app =>
      app[Database].tryTransaction { implicit graph =>
        for {
          richTask     <- app[TaskSrv].create(Task("t1", "default", None, TaskStatus.Waiting, flag = false, None, None, 1, None), None)
          caseTemplate <- app[CaseTemplateSrv].getOrFail("spam")
          _            <- app[CaseTemplateSrv].addTask(caseTemplate, richTask.task)
        } yield ()
      } must beSuccessfulTry

      app[Database].roTransaction { implicit graph =>
        app[CaseTemplateSrv].get("spam").tasks.has("title", "t1").exists()
      } must beTrue
    }

    "update case template tags" in testApp { app =>
      app[Database].tryTransaction { implicit graph =>
        for {
          caseTemplate <- app[CaseTemplateSrv].getOrFail("spam")
          _ <- app[CaseTemplateSrv].updateTagNames(
            caseTemplate,
            Set("""testNamespace.testPredicate="t2"""", """testNamespace.testPredicate="newOne2"""", """newNspc.newPred="newOne3"""")
          )
        } yield ()
      } must beSuccessfulTry
      app[Database].roTransaction { implicit graph =>
        app[CaseTemplateSrv].get("spam").tags.toList.map(_.toString)
      } must containTheSameElementsAs(
        Seq("testNamespace.testPredicate=\"t2\"", "testNamespace.testPredicate=\"newOne2\"", "newNspc.newPred=\"newOne3\"")
      )
    }

    "add tags to a case template" in testApp { app =>
      app[Database].tryTransaction { implicit graph =>
        for {
          caseTemplate <- app[CaseTemplateSrv].getOrFail("spam")
          _            <- app[CaseTemplateSrv].addTags(caseTemplate, Set("""testNamespace.testPredicate="t2"""", """testNamespace.testPredicate="newOne2""""))
        } yield ()
      } must beSuccessfulTry
      app[Database].roTransaction { implicit graph =>
        app[CaseTemplateSrv].get("spam").tags.toList.map(_.toString)
      } must containTheSameElementsAs(
        Seq(
          "testNamespace.testPredicate=\"t2\"",
          "testNamespace.testPredicate=\"newOne2\"",
          "testNamespace.testPredicate=\"spam\"",
          "testNamespace.testPredicate=\"src:mail\""
        )
      )
    }

    "update/create case template custom fields" in testApp { app =>
      app[Database].tryTransaction { implicit graph =>
        for {
          string1      <- app[CustomFieldSrv].getOrFail("string1")
          bool1        <- app[CustomFieldSrv].getOrFail("boolean1")
          integer1     <- app[CustomFieldSrv].getOrFail("integer1")
          caseTemplate <- app[CaseTemplateSrv].getOrFail("spam")
          _            <- app[CaseTemplateSrv].updateCustomField(caseTemplate, Seq((string1, JsString("hate")), (bool1, JsTrue), (integer1, JsNumber(1))))
        } yield ()
      } must beSuccessfulTry

      val expected: Seq[(String, JsValue)] = Seq("string1" -> JsString("hate"), "boolean1" -> JsTrue, "integer1" -> JsNumber(1))
      app[Database].roTransaction { implicit graph =>
        app[CaseTemplateSrv].get("spam").customFields.jsonValue.toList
      } must contain(exactly(expected: _*))
    }
  }
}

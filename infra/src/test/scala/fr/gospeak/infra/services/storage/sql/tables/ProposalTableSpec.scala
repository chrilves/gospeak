package fr.gospeak.infra.services.storage.sql.tables

import fr.gospeak.core.domain._
import fr.gospeak.core.domain.utils.Info
import fr.gospeak.infra.services.storage.sql.tables.ProposalTable._
import fr.gospeak.infra.services.storage.sql.tables.testingutils.TableSpec
import fr.gospeak.libs.scalautils.domain.Markdown

class ProposalTableSpec extends TableSpec {
  private val proposal = Proposal(proposalId, talkId, cfpId, None, Talk.Title("My Proposal"), Proposal.Status.Pending, Markdown("best talk"), Info(userId))

  describe("ProposalTable") {
    describe("insert") {
      it("should generate the query") {
        val q = insert(proposal)
        q.sql shouldBe "INSERT INTO proposals (id, talk_id, cfp_id, event_id, title, status, description, created, created_by, updated, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        check(q)
      }
    }
    describe("selectOne") {
      it("should generate query for proposal id") {
        val q = selectOne(proposalId)
        q.sql shouldBe "SELECT id, talk_id, cfp_id, event_id, title, status, description, created, created_by, updated, updated_by FROM proposals WHERE id=?"
        check(q)
      }
      it("should generate query for talk and cfp ids") {
        val q = selectOne(talkId, cfpId)
        q.sql shouldBe "SELECT id, talk_id, cfp_id, event_id, title, status, description, created, created_by, updated, updated_by FROM proposals WHERE talk_id=? AND cfp_id=?"
        check(q)
      }
    }
    describe("selectPage for group") {
      it("should generate the query") {
        val (s, c) = selectPage(cfpId, params)
        s.sql shouldBe "SELECT id, talk_id, cfp_id, event_id, title, status, description, created, created_by, updated, updated_by FROM proposals WHERE cfp_id=? ORDER BY created DESC OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM proposals WHERE cfp_id=? "
        check(s)
        check(c)
      }
    }
    describe("selectPage for talk") {
      it("should generate the query") {
        val (s, c) = selectPage(talkId, params)
        s.sql shouldBe
          "SELECT c.id, c.slug, c.name, c.description, c.group_id, c.created, c.created_by, c.updated, c.updated_by, " +
            "p.id, p.talk_id, p.cfp_id, p.event_id, p.title, p.status, p.description, p.created, p.created_by, p.updated, p.updated_by " +
            "FROM cfps c INNER JOIN proposals p ON p.cfp_id=c.id WHERE p.talk_id=? ORDER BY p.created DESC OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM cfps c INNER JOIN proposals p ON p.cfp_id=c.id WHERE p.talk_id=? "
        check(s)
        check(c)
      }
    }
  }
}
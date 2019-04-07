package fr.gospeak.infra.services.storage.sql.tables

import cats.data.NonEmptyList
import fr.gospeak.infra.services.storage.sql.tables.EventTable._
import fr.gospeak.infra.services.storage.sql.tables.testingutils.TableSpec

class EventTableSpec extends TableSpec {
  describe("EventTable") {
    describe("insert") {
      it("should generate the query") {
        val q = insert(event)
        q.sql shouldBe "INSERT INTO events (id, group_id, cfp_id, slug, name, start, description, venue, talks, created, created_by, updated, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        check(q)
      }
    }
    describe("update") {
      it("should generate the query") {
        val q = update(group.id, event.slug)(event.data, user.id, now)
        q.sql shouldBe "UPDATE events SET cfp_id=?, slug=?, name=?, start=?, updated=?, updated_by=? WHERE group_id=? AND slug=?"
        check(q)
      }
    }
    describe("updateTalks") {
      it("should generate the query") {
        val q = updateTalks(group.id, event.slug)(Seq(), user.id, now)
        q.sql shouldBe "UPDATE events SET talks=?, updated=?, updated_by=? WHERE group_id=? AND slug=?"
        check(q)
      }
    }
    describe("selectOne") {
      it("should generate the query") {
        val q = selectOne(group.id, event.slug)
        q.sql shouldBe "SELECT id, group_id, cfp_id, slug, name, start, description, venue, talks, created, created_by, updated, updated_by FROM events WHERE group_id=? AND slug=?"
        check(q)
      }
    }
    describe("selectPage") {
      it("should generate the query") {
        val (s, c) = selectPage(group.id, params)
        s.sql shouldBe "SELECT id, group_id, cfp_id, slug, name, start, description, venue, talks, created, created_by, updated, updated_by FROM events WHERE group_id=? ORDER BY start DESC OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM events WHERE group_id=? "
        check(s)
        check(c)
      }
    }
    describe("selectAll") {
      it("should generate the query") {
        val q = selectAll(NonEmptyList.of(event.id))
        q.sql shouldBe "SELECT id, group_id, cfp_id, slug, name, start, description, venue, talks, created, created_by, updated, updated_by FROM events WHERE id IN (?) "
        check(q)
      }
    }
    describe("selectAllAfter") {
      it("should generate the query") {
        val (s, c) = selectAllAfter(group.id, now, params)
        s.sql shouldBe "SELECT id, group_id, cfp_id, slug, name, start, description, venue, talks, created, created_by, updated, updated_by FROM events WHERE group_id=? AND start > ? ORDER BY start DESC OFFSET 0 LIMIT 20"
        c.sql shouldBe "SELECT count(*) FROM events WHERE group_id=? AND start > ? "
        check(s)
        check(c)
      }
    }
  }
}

package com.github.simplesteph.ksm.parser

import java.io.StringReader

import kafka.security.auth._
import org.apache.kafka.common.resource.PatternType
import org.apache.kafka.common.utils.SecurityUtils
import org.scalatest.{FlatSpec, Matchers}

class CsvAclParserTest extends FlatSpec with Matchers {

  val row = Map(
    "KafkaPrincipal" -> "User:alice",
      "ResourceType" -> "Topic",
      "PatternType" -> "LITERAL",
      "ResourceName" -> "test",
      "Operation" -> "Read",
      "PermissionType" -> "Allow",
      "Host" -> "*",
  )

  val resource = Resource(Topic, "test", PatternType.LITERAL)
  val acl = Acl(SecurityUtils.parseKafkaPrincipal("User:alice"), Allow, "*", Read)

  "parseRow" should "correctly parse a Row" in {
    CsvAclParser.parseRow(row) shouldBe((resource, acl))
  }

  "aclsFromCsv" should "correctly parse a Correct CSV" in {
    val csv =
      """KafkaPrincipal,ResourceType,PatternType,ResourceName,Operation,PermissionType,Host
        |User:alice,Topic,LITERAL,foo,Read,Allow,*
        |User:bob,Group,LITERAL,bar,Write,Deny,12.34.56.78
        |
        |User:peter,Cluster,LITERAL,kafka-cluster,Create,Allow,*
        |""".stripMargin


    val acl1 = Acl(SecurityUtils.parseKafkaPrincipal("User:alice"), Allow, "*", Read)
    val acl2 = Acl(SecurityUtils.parseKafkaPrincipal("User:bob"), Deny, "12.34.56.78", Write)
    val acl3 = Acl(SecurityUtils.parseKafkaPrincipal("User:peter"), Allow, "*", Create)

    val res1 = Resource(Topic, "foo", PatternType.LITERAL)
    val res2 = Resource(Group, "bar", PatternType.LITERAL)
    val res3 = Resource(Cluster, "kafka-cluster", PatternType.LITERAL)

    val res = CsvAclParser.aclsFromReader(new StringReader(csv))

    res.errs shouldBe List()

    res.acls shouldBe Set(
      res1 -> acl1,
      res2 -> acl2,
      res3 -> acl3
    )

  }


  "aclsFromCsv" should "catch all errors and catch all correct" in {

    // 1 correct, 1 wrong data, 1 missing column
    val csv =
      """KafkaPrincipal,ResourceType,PatternType,ResourceName,Operation,PermissionType,Host
        |User:alice,Topic,LITERAL,foo,Read,Allow,*
        |User:bob,Group,PREFIXED,bar,Wrong,Deny,12.34.56.78
        |User:peter,Cluster,LITERAL,kafka-cluster,Create,Allow
        |""".stripMargin


    val acl1 = Acl(SecurityUtils.parseKafkaPrincipal("User:alice"), Allow, "*", Read)
    val acl2 = Acl(SecurityUtils.parseKafkaPrincipal("User:bob"), Deny, "12.34.56.78", Write)
    val acl3 = Acl(SecurityUtils.parseKafkaPrincipal("User:peter"), Allow, "*", Create)

    val res1 = Resource(Topic, "foo", PatternType.LITERAL)
    val res2 = Resource(Group, "bar", PatternType.LITERAL)
    val res3 = Resource(Cluster, "kafka-cluster", PatternType.LITERAL)

    val res = CsvAclParser.aclsFromReader(new StringReader(csv))

    res.errs.size shouldBe 2
    val throwable1 = res.errs.head.get
    throwable1.getClass shouldBe classOf[CsvParserException]
    throwable1.asInstanceOf[CsvParserException].printRow() should include("bob")

    val throwable2 = res.errs.head.get
    throwable2.getClass shouldBe classOf[CsvParserException]

    res.acls shouldBe Set(
      res1 -> acl1,
    )

  }


  "aclsFromCsv" should "complain if one header is missing" in {

    // 1 correct, 1 wrong data, 1 missing column
    val csv =
      """KafkaPrincipal,ResourceType,PatternType,ResourceName,PermissionType,Host
        |User:alice,Topic,LITERAL,foo,Read,Allow,*
        |User:bob,Group,PREFIXED,bar,Wrong,Deny,12.34.56.78
        |User:peter,Cluster,LITERAL,kafka-cluster,Create,Allow
        |""".stripMargin

    val res = CsvAclParser.aclsFromReader(new StringReader(csv))

    res.errs.size shouldBe 3

  }

  "asCsv" should "correctly write CSV Row" in {
    val acl1 = Acl(SecurityUtils.parseKafkaPrincipal("User:alice"), Allow, "*", Read)
    val res1 = Resource(Topic, "foo", PatternType.LITERAL)
    val res = CsvAclParser.asCsv(res1, acl1)
    res shouldBe "User:alice,Topic,LITERAL,foo,Read,Allow,*"
  }

  "asCsv" should "correctly format entire ACL" in {
    val csv =
      """KafkaPrincipal,ResourceType,PatternType,ResourceName,Operation,PermissionType,Host
        |User:alice,Topic,LITERAL,foo,Read,Allow,*
        |User:bob,Group,PREFIXED,bar,Write,Deny,12.34.56.78
        |User:peter,Cluster,LITERAL,kafka-cluster,Create,Allow,*
        |""".stripMargin


    val acl1 = Acl(SecurityUtils.parseKafkaPrincipal("User:alice"), Allow, "*", Read)
    val acl2 = Acl(SecurityUtils.parseKafkaPrincipal("User:bob"), Deny, "12.34.56.78", Write)
    val acl3 = Acl(SecurityUtils.parseKafkaPrincipal("User:peter"), Allow, "*", Create)

    val res1 = Resource(Topic, "foo", PatternType.LITERAL)
    val res2 = Resource(Group, "bar", PatternType.PREFIXED)
    val res3 = Resource(Cluster, "kafka-cluster", PatternType.LITERAL)

    val res = CsvAclParser.formatAcls(List((res1, acl1),(res2, acl2), (res3, acl3)))

    res shouldBe csv

  }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.execution.datasources

import java.net.URI

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileStatus, Path, RawLocalFileSystem}
import org.scalatest.PrivateMethodTester

import org.apache.spark.sql.AnalysisException
import org.apache.spark.sql.test.SharedSparkSession

class DataSourceSuite extends SharedSparkSession with PrivateMethodTester {

  import TestPaths._

  test("test glob and non glob paths") {
    val resultPaths = DataSource.checkAndGlobPathIfNecessary(
      Seq(
        path1.toString,
        path2.toString,
        globPath1.toString,
        globPath2.toString
      ),
      hadoopConf,
      checkEmptyGlobPath = true,
      checkFilesExist = true,
      enableGlobbing = true
    )

    assert(resultPaths.toSet === allPathsInFs.toSet)
  }

  test("test glob paths") {
    val resultPaths = DataSource.checkAndGlobPathIfNecessary(
      Seq(
        globPath1.toString,
        globPath2.toString
      ),
      hadoopConf,
      checkEmptyGlobPath = true,
      checkFilesExist = true,
      enableGlobbing = true
    )

    assert(
      resultPaths.toSet === Set(
        globPath1Result1,
        globPath1Result2,
        globPath2Result1,
        globPath2Result2
      )
    )
  }

  test("test non glob paths") {
    val resultPaths = DataSource.checkAndGlobPathIfNecessary(
      Seq(
        path1.toString,
        path2.toString
      ),
      hadoopConf,
      checkEmptyGlobPath = true,
      checkFilesExist = true,
      enableGlobbing = true
    )

    assert(
      resultPaths.toSet === Set(
        path1,
        path2
      )
    )
  }

  test("test non glob paths checkFilesExist=false") {
    val resultPaths = DataSource.checkAndGlobPathIfNecessary(
      Seq(
        path1.toString,
        path2.toString,
        nonExistentPath.toString
      ),
      hadoopConf,
      checkEmptyGlobPath = true,
      checkFilesExist = false,
      enableGlobbing = true
    )

    assert(
      resultPaths.toSet === Set(
        path1,
        path2,
        nonExistentPath
      )
    )
  }

  test("test non existent paths") {
    assertThrows[AnalysisException](
      DataSource.checkAndGlobPathIfNecessary(
        Seq(
          path1.toString,
          path2.toString,
          nonExistentPath.toString
        ),
        hadoopConf,
        checkEmptyGlobPath = true,
        checkFilesExist = true,
        enableGlobbing = true
      )
    )
  }

  test("test non existent glob paths") {
    assertThrows[AnalysisException](
      DataSource.checkAndGlobPathIfNecessary(
        Seq(
          globPath1.toString,
          globPath2.toString,
          nonExistentGlobPath.toString
        ),
        hadoopConf,
        checkEmptyGlobPath = true,
        checkFilesExist = true,
        enableGlobbing = true
      )
    )
  }

  test("Data source options should be propagated in method checkAndGlobPathIfNecessary") {
    val dataSourceOptions = Map("fs.defaultFS" -> "nonexistentFs://nonexistentFs")
    val dataSource = DataSource(spark, "parquet", Seq("/path3"), options = dataSourceOptions)
    val checkAndGlobPathIfNecessary =
      PrivateMethod[Seq[Path]](Symbol("checkAndGlobPathIfNecessary"))

    val message = intercept[java.io.IOException] {
      dataSource invokePrivate checkAndGlobPathIfNecessary(false, false)
    }.getMessage
    val expectMessage = "No FileSystem for scheme nonexistentFs"
    assert(message.filterNot(Set(':', '"').contains) == expectMessage)
  }

  test("SPARK-39910: test Hadoop archive non glob paths") {
    val absoluteHarPaths = buildFullHarPaths(allRelativeHarPaths)

    val resultPaths = DataSource.checkAndGlobPathIfNecessary(
      absoluteHarPaths.map(_.toString),
      hadoopConf,
      checkEmptyGlobPath = true,
      checkFilesExist = true,
      enableGlobbing = true
    )

    assert(
      resultPaths.toSet === absoluteHarPaths.toSet
    )
  }

  test("SPARK-39910: test Hadoop archive glob paths") {
    val harGlobePaths = buildFullHarPaths(Seq(globeRelativeHarPath))

    val resultPaths = DataSource.checkAndGlobPathIfNecessary(
      harGlobePaths.map(_.toString),
      hadoopConf,
      checkEmptyGlobPath = true,
      checkFilesExist = true,
      enableGlobbing = true
    )

    val expectedHarPaths = buildFullHarPaths(allRelativeHarPaths)
    assert(
      resultPaths.toSet === expectedHarPaths.toSet
    )
  }
}

object TestPaths {
  val hadoopConf = new Configuration()
  hadoopConf.set("fs.mockFs.impl", classOf[MockFileSystem].getName)

  val path1 = new Path("mockFs://mockFs/somepath1")
  val path2 = new Path("mockFs://mockFs/somepath2")
  val globPath1 = new Path("mockFs://mockFs/globpath1*")
  val globPath2 = new Path("mockFs://mockFs/globpath2*")

  val nonExistentPath = new Path("mockFs://mockFs/nonexistentpath")
  val nonExistentGlobPath = new Path("mockFs://mockFs/nonexistentpath*")

  val globPath1Result1 = new Path("mockFs://mockFs/globpath1/path1")
  val globPath1Result2 = new Path("mockFs://mockFs/globpath1/path2")
  val globPath2Result1 = new Path("mockFs://mockFs/globpath2/path1")
  val globPath2Result2 = new Path("mockFs://mockFs/globpath2/path2")

  val allPathsInFs = Seq(
    path1,
    path2,
    globPath1Result1,
    globPath1Result2,
    globPath2Result1,
    globPath2Result2
  )

  val mockGlobResults: Map[Path, Array[FileStatus]] = Map(
    globPath1 ->
      Array(
        createMockFileStatus(globPath1Result1.toString),
        createMockFileStatus(globPath1Result2.toString)
      ),
    globPath2 ->
      Array(
        createMockFileStatus(globPath2Result1.toString),
        createMockFileStatus(globPath2Result2.toString)
      )
  )

  val txtRelativeHarPath = new Path("/test.txt")
  val csvRelativeHarPath = new Path("/test.csv")
  val jsonRelativeHarPath = new Path("/test.json")
  val parquetRelativeHarPath = new Path("/test.parquet")
  val orcRelativeHarPath = new Path("/test.orc")
  val globeRelativeHarPath = new Path("/test.*")

  val allRelativeHarPaths = Seq(
    txtRelativeHarPath,
    csvRelativeHarPath,
    jsonRelativeHarPath,
    parquetRelativeHarPath,
    orcRelativeHarPath
  )

  def createMockFileStatus(path: String): FileStatus = {
    val fileStatus = new FileStatus()
    fileStatus.setPath(new Path(path))
    fileStatus
  }

  def buildFullHarPaths(relativePaths: Seq[Path]): Seq[Path] = {
    val archiveUrl = Thread.currentThread().getContextClassLoader
      .getResource("test-data/test-archive.har")
      .getPath

    val archivePath = new Path("har", "file-:", archiveUrl)
    relativePaths.map(Path.mergePaths(archivePath, _))
  }
}

class MockFileSystem extends RawLocalFileSystem {
  import TestPaths._

  override def exists(f: Path): Boolean = {
    allPathsInFs.contains(f)
  }

  override def globStatus(pathPattern: Path): Array[FileStatus] = {
    mockGlobResults.getOrElse(pathPattern, Array())
  }

  override def getUri: URI = URI.create("mockFs://mockFs/")
}

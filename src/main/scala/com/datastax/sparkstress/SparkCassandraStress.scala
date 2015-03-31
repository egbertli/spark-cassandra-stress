package com.datastax.sparkstress

import org.apache.spark.{SparkConf, SparkContext}


case class Config(
  //Test Options
  testName: String ="writeshortrow",
  keyspace: String = "ks",
  table: String = "tab",
  numPartitions: Int = 400,
  totalOps: Long = 20 * 1000000,
  numTotalKeys: Long =  1 * 1000000,
  trials: Int = 1,
  deleteKeyspace: Boolean = false,
  //Spark Options
  sparkOps: Map[String,String] = Map.empty
)



object SparkCassandraStress {
  val VALID_TESTS =
    WriteTask.ValidTasks ++
    Set("readall")

  def main(args: Array[String]) {

    val parser = new scopt.OptionParser[Config]("SparkCassandraStress") {
      head("SparkCassandraStress", "1.0")

      arg[String]("testName") optional() action { (arg,config) =>
        config.copy(testName = arg.toLowerCase())
      } text {"Name of the test to be run: "+VALID_TESTS.mkString(" , ")}

      arg[String]("master") optional() action { (arg,config) =>
        config.copy(sparkOps = config.sparkOps + ("spark.master" -> arg))
      } text {"Spark Address of Master Node"}

      arg [String]("cassandra") optional() action { (arg, config) =>
        config.copy(sparkOps = config.sparkOps + ("spark.cassandra.connection.host" -> arg))
      } text {"Ip Address to Connect To Cassandra On"}

      opt[Long]('n',"totalOps") optional() action { (arg,config) =>
        config.copy(totalOps = arg)
      } text {"Total number of operations to execute"}

      opt[Int]('x',"numPartitons") optional() action { (arg,config) =>
        config.copy(numPartitions = arg)
      } text {"Number of Spark Partitions To Create"}

      opt[Long]('k',"numTotalKeys") optional() action { (arg,config) =>
        config.copy(numTotalKeys = arg)
      } text {"Total Number of CQL Partition Key Values"}

      opt[Int]('t',"trials")optional() action { (arg,config) =>
        config.copy(trials = arg)
      } text {"Trials to run"}

      opt[Unit]('d',"deleteKeyspace") optional() action { (_,config) =>
        config.copy(deleteKeyspace = true)
      } text {"Delete Keyspace before running"}

      opt[Int]('p',"maxParaWrites") optional() action { (arg,config) =>
        config.copy(sparkOps = config.sparkOps + ("spark.cassandra.output.concurrent.writes" -> arg.toString))
      } text {"Connector Write Paralellism"}

      opt[Int]('b',"batchSize") optional() action { (arg,config) =>
        config.copy(sparkOps = config.sparkOps + ("spark.cassandra.output.batch.size.bytes" -> arg.toString))
      } text {"Write Batch Size in bytes"}

      opt[Int]('r',"rowSize") optional() action { (arg,config) =>
        config.copy(sparkOps = config.sparkOps + ("spark.cassandra.output.batch.size.rows" -> arg.toString))
      } text {"This setting will override batch size in bytes and instead just do a static number of rows per batch"}

      opt[Int]('f',"fetchSize") optional() action { (arg,config) =>
        config.copy(sparkOps = config.sparkOps + ("spark.cassandra.input.page.row.size" -> arg.toString))
      } text {"Read fetch size"}

      opt[Int]('s',"splitSize") optional() action { (arg,config) =>
        config.copy(sparkOps = config.sparkOps + ("spark.cassandra.input.split.size" -> arg.toString))
      } text {"Read input size"}
      help("help") text {"CLI Help"}

     checkConfig{ c => if (VALID_TESTS.contains(c.testName)) success else failure(c.testName+" is not a valid test : "+VALID_TESTS.mkString(" , ")) }
    }

    parser.parse(args, Config()) map { config =>
      runTask(config)
    } getOrElse {
      System.exit(1)
    }
  }

  def runTask(config:Config)
  {

    val sparkConf =
      new SparkConf()
        .setAppName("SparkStress: "+config.testName)
        .setAll(config.sparkOps)

    val sc = new SparkContext(sparkConf)

    val test: StressTask =
      config.testName.toLowerCase match {
        case "writeshortrow" => new WriteShortRow(config, sc)
        case "writewiderow" => new WriteWideRow(config, sc)
        case "writeperfrow" => new WritePerfRow(config, sc)
        case "writerandomwiderow" => new WriteRandomWideRow(config, sc)
        case "readall" => new ReadAll()
      }

    test.setConfig(config)

    val time = test.runTrials(sc)
    sc.stop()
    val timeSeconds = time.map{ _ / 1000000000.0}
    val opsPerSecond = timeSeconds.map{ config.totalOps/_}
    printf(s"\n\nTimes Ran : %s\n",timeSeconds.mkString(","))
    printf(s"\n\nOpsPerSecond : %s\n",opsPerSecond.mkString(","))
 }



}

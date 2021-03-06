package streaming.core.datasource

import java.util.concurrent.TimeUnit

import org.apache.spark.sql.streaming.{DataStreamWriter, Trigger}
import org.apache.spark.sql.{DataFrameWriter, Row}
import streaming.dsl.{DslTool, ScriptSQLExec}

/**
  * 2019-03-20 WilliamZhu(allwefantasy@gmail.com)
  */
abstract class MLSQLBaseStreamSource extends MLSQLSource with MLSQLSink with MLSQLSourceInfo with MLSQLRegistry with DslTool {

  def rewriteConfig(config: Map[String, String]) = {
    config
  }


  override def save(batchWriter: DataFrameWriter[Row], config: DataSinkConfig): Any = {
    val oldDF = config.df.get
    var option = config.config
    if (option.contains("fileNum")) {
      option -= "fileNum"
    }

    val writer: DataStreamWriter[Row] = oldDF.writeStream
    var path = config.path

    val Array(db, table) = parseRef(aliasFormat, path, (options: Map[String, String]) => {
      writer.options(options)
    })

    path = table

    require(option.contains("checkpointLocation"), "checkpointLocation is required")
    require(option.contains("duration"), "duration is required")
    require(option.contains("mode"), "mode is required")

    if (option.contains("partitionByCol")) {
      val cols = option("partitionByCol").split(",").filterNot(f => f.isEmpty)
      if (cols.size != 0) {
        writer.partitionBy(option("partitionByCol").split(","): _*)
      }
      option -= "partitionByCol"
    }

    val duration = option("duration").toInt
    option -= "duration"


    val mode = option("mode")
    option -= "mode"

    val format = config.config.getOrElse("implClass", fullFormat)

    writer.format(format).outputMode(mode).options(option)

    val dbtable = if (option.contains("dbtable")) option("dbtable") else path

    if (dbtable != null && dbtable != "-") {
      writer.option("path", dbtable)
    }

    ScriptSQLExec.contextGetOrForTest().execListener.env().get("streamName") match {
      case Some(name) => writer.queryName(name)
      case None =>
    }
    writer.trigger(Trigger.ProcessingTime(duration, TimeUnit.SECONDS)).start()
  }


  override def register(): Unit = {
    DataSourceRegistry.register(MLSQLDataSourceKey(fullFormat, MLSQLSparkDataSourceType), this)
    DataSourceRegistry.register(MLSQLDataSourceKey(shortFormat, MLSQLSparkDataSourceType), this)
  }

  override def sourceInfo(config: DataAuthConfig): SourceInfo = {

    val Array(db, table) = config.path.split("\\.") match {
      case Array(db, table) => Array(db, table)
      case Array(table) => Array("", table)
    }
    SourceInfo(shortFormat, db, table)
  }
}

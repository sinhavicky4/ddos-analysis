
import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.{col, from_json, split, to_timestamp}
import org.apache.spark.sql.types.{StringType, StructType}

object StreamLogAnalysis {

  def main(args: Array[String]): Unit = {
    Logger.getLogger("org").setLevel(Level.OFF)
    Logger.getLogger("akka").setLevel(Level.OFF)

    val spark = SparkSession
      .builder()
      .appName("DDOS Log Analysis").config("spark.sql.warehouse.dir","warehouse/")
      .master("local[2]")
      .getOrCreate()

    import spark.implicits._

    val schema = new StructType()
      .add("@metadata", StringType, true)
      .add("@timestamp", StringType, true)
      .add("log", StringType, true)
      .add("message", StringType, true)
      .add("input", StringType, true)
      .add("esc", StringType, true)
      .add("host", StringType, true)
      .add("agent", StringType, true)

    val df = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "localhost:9092")
      .option("startingOffsets", "earliest")
      .option("enable.auto.commit", false)
      .option("subscribe", "log-events")
      .load().selectExpr("CAST(key AS STRING)", "CAST(value AS STRING) as json")
      .select(from_json(col("json"), schema).as("data")).where("data is not null")

    val kDf = df.withColumn("log", col("data").getField("message")).drop("data")

    val fDf = kDf.map(f => {
      val elements = f.getString(0).split(" ")
      (elements(0),
        //elements(1),elements(2),
        elements(3).replace("[", "") + " " + elements(4).replace("]", ""), //.replaceAll("[^a-zA-Z0-9:]", " ").trim(),
        //elements(4).replace("]",""),
        elements(5).replaceAll("[^a-zA-Z0-9]", ""),
        //elements(6),
        elements(7).replaceAll("[^a-zA-Z0-9/.]", ""),
        elements(8),
        elements(9)
      )
    }).toDF("ip", "logTime", "method", "protocol", "status", "resonseTime")
    var query = fDf.writeStream.format("console").start()

    query.awaitTermination()


  }
}

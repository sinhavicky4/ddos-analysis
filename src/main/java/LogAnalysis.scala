
import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.{col, date_format, to_date, to_timestamp, to_utc_timestamp, unix_timestamp}
import org.apache.spark.sql.types.TimestampType

import java.sql.Timestamp

object LogAnalysis {

  def main(args: Array[String]): Unit = {
    Logger.getLogger("org").setLevel(Level.OFF)
    Logger.getLogger("akka").setLevel(Level.OFF)

    val spark = SparkSession
      .builder()
      .appName("DDOS Log Analysis")
      .master("local[2]")
      .getOrCreate()
    var input = spark.read.text("/home/priynashu/phData/*.txt")
    import spark.implicits._
    val logDf = input.map(f=>{
      val elements = f.getString(0).split(" ")
      (elements(0),
        //elements(1),elements(2),
        elements(3).replace("[","")+" "+elements(4).replace("]",""),//.replaceAll("[^a-zA-Z0-9:]", " ").trim(),
        //elements(4).replace("]",""),
        elements(5).replaceAll("[^a-zA-Z0-9]", ""),
        //elements(6),
        elements(7).replaceAll("[^a-zA-Z0-9/.]", ""),
        elements(8),
        elements(9)
      )
    }).toDF("ip","logTime","method","protocol","status","resonseTime")

    //logDf.show(truncate = false)
    val ts = to_timestamp($"logTime", "dd/MMM/yyyy:HH:mm:ss ZZZ")
    val finalLogDf=logDf.withColumn("logTimeStamp",ts).drop("logtime")
    finalLogDf.show()
    print("total count"+logDf.count())
    //logDf.groupBy("ip").count().where("count >10").orderBy($"count".desc).show(truncate = false)



  }
}

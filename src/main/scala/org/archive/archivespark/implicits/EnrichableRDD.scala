/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2018 Helge Holzmann (L3S) and Vinay Goel (Internet Archive)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.archive.archivespark.implicits

import org.apache.spark.rdd.RDD
import org.apache.spark.sql._
import org.archive.archivespark.model._
import org.archive.archivespark.model.pointers.FieldPointer
import org.archive.archivespark.sparkling.Sparkling

import scala.collection.TraversableOnce
import scala.reflect.ClassTag
import scala.util.Try

class EnrichableRDD[Root <: EnrichRoot : ClassTag](rdd: RDD[Root]) {
  def enrich[R >: Root <: EnrichRoot : ClassTag](func: EnrichFunc[R, _, _]): RDD[Root] = Sparkling.initPartitions(rdd).mapPartitions { records =>
    records.map(func.enrich)
  }

  def filterExists[R >: Root <: EnrichRoot](pointer: FieldPointer[R, _]): RDD[Root] = rdd.filter(pointer.exists(_))

  def filterNoException(): RDD[Root] = rdd.filter(r => r.lastException.isEmpty)
  def lastException: Option[Exception] = Try{rdd.filter(r => r.lastException.isDefined).take(1).head.lastException.get}.toOption
  def throwLastException(): Unit = lastException match {
    case Some(e) => throw e
    case _ =>
  }

  def filterValue[R >: Root <: EnrichRoot, T](pointer: FieldPointer[R, T])(filter: Option[T] => Boolean): RDD[Root] = {
    rdd.filter(r => filter(pointer.get(r)))
  }

  def filterNonEmpty[R >: Root <: EnrichRoot, T <: {def nonEmpty: Boolean}](pointer: FieldPointer[R, T]): RDD[Root] = {
    rdd.filter{r =>
      pointer.get(r) match {
        case Some(value) => value.nonEmpty
        case _ => false
      }
    }
  }

  def distinctValue[T : ClassTag](value: Root => T)(distinct: (Root, Root) => Root): RDD[Root] = rdd.distinctByValue(value)(distinct)
  def distinctValue[R >: Root <: EnrichRoot, T](pointer: FieldPointer[R, T])(distinct: (Root, Root) => Root): RDD[Root] = {
    rdd.map(r => (pointer.get(r), r)).reduceByKey(distinct, Sparkling.parallelism).values
  }

  def mapValues[R >: Root <: EnrichRoot, T : ClassTag](pointer: FieldPointer[R, T]): RDD[T] = rdd.map(r => pointer.get(r)).filter(_.isDefined).map(_.get)

  def flatMapValues[R >: Root <: EnrichRoot, T : ClassTag, S <: TraversableOnce[T] : ClassTag](pointer: FieldPointer[R, S]): RDD[T] = mapValues(pointer).flatMap(r => r)

  def toDataFrame: DataFrame = {
    val sql = SparkSession.builder.getOrCreate()
    sql.read.json(rdd.toJsonStrings(pretty = false))
  }
}

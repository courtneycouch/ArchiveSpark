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

package org.archive.archivespark.enrich

import org.archive.archivespark.enrich.dataloads.{DataLoadBase, DataLoadCompanion}
import org.archive.archivespark.enrich.functions.DataLoad

abstract class BasicMultiValEnrichFunc[DataLoad <: DataLoadBase, Input, Output](dataLoad: DataLoadCompanion[DataLoad], name: String, body: TypedEnrichable[Input] => Option[Seq[Output]])
  extends DependentEnrichFuncWithDefaultField[EnrichRoot with DataLoad, Input, Seq[Output], Seq[Output]] with SingleField[Seq[Output]] {

  override def dependency: EnrichFunc[EnrichRoot with DataLoad, _] = DataLoad(dataLoad.Field)
  override def dependencyField: String = dataLoad.Field

  override def resultField = name

  override def derive(source: TypedEnrichable[Input], derivatives: Derivatives): Unit = for (value <- body(source)) {
    derivatives.setNext(MultiValueEnrichable(value))
  }
}
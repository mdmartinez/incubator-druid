/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.druid.query.aggregation.last;

import io.druid.java.util.common.Pair;
import io.druid.query.aggregation.SerializablePairLongString;
import io.druid.query.aggregation.TestLongColumnSelector;
import io.druid.query.aggregation.TestObjectColumnSelector;
import io.druid.segment.ColumnSelectorFactory;
import io.druid.segment.column.Column;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;

public class StringLastAggregationTest
{
  private final Integer MAX_STRING_SIZE = 1024;
  private StringLastAggregatorFactory stringLastAggFactory;
  private StringLastAggregatorFactory combiningAggFactory;
  private ColumnSelectorFactory colSelectorFactory;
  private TestLongColumnSelector timeSelector;
  private TestObjectColumnSelector<String> valueSelector;
  private TestObjectColumnSelector objectSelector;

  private String[] strings = {"1111", "2222", "3333", null, "4444"};
  private long[] times = {8224, 6879, 2436, 3546, 7888};
  private SerializablePairLongString[] pairs = {
      new SerializablePairLongString(52782L, "AAAA"),
      new SerializablePairLongString(65492L, "BBBB"),
      new SerializablePairLongString(69134L, "CCCC"),
      new SerializablePairLongString(11111L, "DDDD"),
      new SerializablePairLongString(51223L, null)
  };

  @Before
  public void setup()
  {
    stringLastAggFactory = new StringLastAggregatorFactory("billy", "nilly", MAX_STRING_SIZE);
    combiningAggFactory = (StringLastAggregatorFactory) stringLastAggFactory.getCombiningFactory();
    timeSelector = new TestLongColumnSelector(times);
    valueSelector = new TestObjectColumnSelector<>(strings);
    objectSelector = new TestObjectColumnSelector<>(pairs);
    colSelectorFactory = EasyMock.createMock(ColumnSelectorFactory.class);
    EasyMock.expect(colSelectorFactory.makeColumnValueSelector(Column.TIME_COLUMN_NAME)).andReturn(timeSelector);
    EasyMock.expect(colSelectorFactory.makeColumnValueSelector("nilly")).andReturn(valueSelector);
    EasyMock.expect(colSelectorFactory.makeColumnValueSelector("billy")).andReturn(objectSelector);
    EasyMock.replay(colSelectorFactory);
  }

  @Test
  public void testStringLastAggregator()
  {
    StringLastAggregator agg = (StringLastAggregator) stringLastAggFactory.factorize(colSelectorFactory);

    aggregate(agg);
    aggregate(agg);
    aggregate(agg);
    aggregate(agg);

    Pair<Long, String> result = (Pair<Long, String>) agg.get();

    Assert.assertEquals(strings[0], result.rhs);
  }

  @Test
  public void testStringLastBufferAggregator()
  {
    StringLastBufferAggregator agg = (StringLastBufferAggregator) stringLastAggFactory.factorizeBuffered(
        colSelectorFactory);

    ByteBuffer buffer = ByteBuffer.wrap(new byte[stringLastAggFactory.getMaxIntermediateSize()]);
    agg.init(buffer, 0);

    aggregate(agg, buffer, 0);
    aggregate(agg, buffer, 0);
    aggregate(agg, buffer, 0);
    aggregate(agg, buffer, 0);

    Pair<Long, String> result = (Pair<Long, String>) agg.get(buffer, 0);

    Assert.assertEquals(strings[0], result.rhs);
  }

  @Test
  public void testCombine()
  {
    SerializablePairLongString pair1 = new SerializablePairLongString(1467225000L, "AAAA");
    SerializablePairLongString pair2 = new SerializablePairLongString(1467240000L, "BBBB");
    Assert.assertEquals(pair2, stringLastAggFactory.combine(pair1, pair2));
  }

  @Test
  public void testStringLastCombiningAggregator()
  {
    StringLastAggregator agg = (StringLastAggregator) combiningAggFactory.factorize(colSelectorFactory);

    aggregate(agg);
    aggregate(agg);
    aggregate(agg);
    aggregate(agg);

    Pair<Long, String> result = (Pair<Long, String>) agg.get();
    Pair<Long, String> expected = (Pair<Long, String>) pairs[2];

    Assert.assertEquals(expected.lhs, result.lhs);
    Assert.assertEquals(expected.rhs, result.rhs);
  }

  @Test
  public void testStringLastCombiningBufferAggregator()
  {
    StringLastBufferAggregator agg = (StringLastBufferAggregator) combiningAggFactory.factorizeBuffered(
        colSelectorFactory);

    ByteBuffer buffer = ByteBuffer.wrap(new byte[stringLastAggFactory.getMaxIntermediateSize()]);
    agg.init(buffer, 0);

    aggregate(agg, buffer, 0);
    aggregate(agg, buffer, 0);
    aggregate(agg, buffer, 0);
    aggregate(agg, buffer, 0);

    Pair<Long, String> result = (Pair<Long, String>) agg.get(buffer, 0);
    Pair<Long, String> expected = (Pair<Long, String>) pairs[2];

    Assert.assertEquals(expected.lhs, result.lhs);
    Assert.assertEquals(expected.rhs, result.rhs);
  }

  @Test
  public void testStringLastAggregateCombiner()
  {
    final String[] strings = {"AAAA", "BBBB", "CCCC", "DDDD", "EEEE"};
    TestObjectColumnSelector columnSelector = new TestObjectColumnSelector<>(strings);

    StringLastAggregateCombiner stringFirstAggregateCombiner =
        (StringLastAggregateCombiner) combiningAggFactory.makeAggregateCombiner();

    stringFirstAggregateCombiner.reset(columnSelector);

    Assert.assertEquals(strings[0], stringFirstAggregateCombiner.getObject());

    columnSelector.increment();
    stringFirstAggregateCombiner.fold(columnSelector);

    Assert.assertEquals(strings[1], stringFirstAggregateCombiner.getObject());

    stringFirstAggregateCombiner.reset(columnSelector);

    Assert.assertEquals(strings[1], stringFirstAggregateCombiner.getObject());
  }

  private void aggregate(
      StringLastAggregator agg
  )
  {
    agg.aggregate();
    timeSelector.increment();
    valueSelector.increment();
    objectSelector.increment();
  }

  private void aggregate(
      StringLastBufferAggregator agg,
      ByteBuffer buff,
      int position
  )
  {
    agg.aggregate(buff, position);
    timeSelector.increment();
    valueSelector.increment();
    objectSelector.increment();
  }
}

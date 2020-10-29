```
1. 提供足够的文件缓存
2. 使用更快的硬件
3. 调整文档模型 避免使用 关联（nested join）
4. 尽可能少地搜索字段（cpoy_to）
5. 索引前数据编辑 （比如一个range查询可以根据实际情况转为term查询  比如 公司按注册资本查询0-100万 100-1000万 1000万以上三类型  可以归纳成0-100万 100-1000万 1000万3个term 替换原有数值）
6. 将数值转换成keyword （一些不需要的rang的数值标识转换成keyword  term效率高于range  PointRangeQuery SortedSetDocValuesRangeQuery IndexOrDocValuesQuery ）
7. 避免使用脚本
8. 查询日期四舍五入（多个用户可能落在同一区间，充分利用缓存）
9. 强制合并只读索引
10. warm up global ordinals（增加agg的效率）
11. 预热文件系统缓存 （index.store.preload， "nvd"：norms"dvd"：docvalues"tim"：termsdictionaries"doc"：postingslists"dim"：points 通过配置文件扩展名明确告诉操作系统哪些文件应该急切地加载到内存中， 缓存不够大会导致效率降低 慎用）
12. 使用index sorting ({"settings":{"index":{"sort.field":"enter_time","sort.order":"desc"}}  提前中断查询  1. 查询字段和排序字段相同 2. 不关心排序 )
13. 使用 preference 参数提高缓存使用率 （慎重）
14.  备份也会参与查询，这有助于提高吞吐量，需要正确的设置 max(max_failures, ceil(num_nodes / num_primaries) - 1)
15. 打开自动副本选择 {"transient":{"cluster.routing.use_adaptive_replica_selection":true}}
16. 大文档 exclude把大字段排除_source之外
```

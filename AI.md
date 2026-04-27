## Model规范
Model分为数据库实体, 显示提示, 数据传输实体. 应遵循最大化复用的原则, 例如数据库实体可以直接作为数据传输实体或显示实体, 只有当数据库实体不适合直接作为数据传输实体或显示实体时, 才需要单独创建数据传输实体或显示实体. 显示实体和数据传输实体都应实现`fromDatabaseModel`静态方法, 用于从数据库实体转换而来. 数据传输实体还应实现`toDatabaseModel`方法, 用于转换回数据库实体. 显示实体还应实现`toDataTransferModel`方法, 用于转换为数据传输实体. 每个类和字段都要有文档注释, 以便用户理解接口的数据结构和使用方法.

实体创建示例:

```java
import ink.icoding.smartmybatis.entity.po.enums.TableName;

import java.util.Map;

@TableName("USER")
public class User extends PO {
  @ID
  private int id;
  @FieldName(value = "USER_NAME", description = "用户名", length = 50)
  private String userName;
  @FieldName(value = "EMAIL", description = "邮箱")
  private String email;
  @FieldName(value = "PASSWORD", description = "密码")
  private String password;
  @FieldName(value = "DEPT_ID", description = "部门ID")
  private String deptId;

  /**
   * 部门名称, 通过deptId关联Dept表的id字段获取部门名称, 仅用于显示, 不存储到数据库中, 属于虚拟字段
   */
  @TableField(exist = false, link = Dept.class, linkField = "name", self = "deptId", target = "id")
  private String deptName;

  /**
   * 性别, 任何实体都可以使用枚举类, SmartMybatis会自动处理.
   */
  @FieldName(value = "SEX", description = "性别, MALE=男性, FEMALE=女性")
  private Sex sex;

  @TableField(json = true, description = "额外信息, JSON格式存储, 可以存储任意结构的数据, 以便后续扩展使用")
  private Map<String, String> extra;
}

@TableName("DEPT")
public class Dept extends PO {
  @ID
  private int id;
  @FieldName(value = "NAME", description = "部门名称")
  private String name;
}

public class UserVO extends User {
  public static UserVO fromDatabaseModel(User user) {
    UserVO userVO = new UserVO();
    userVO.setId(user.getId());
    userVO.setUserName(user.getUserName());
    userVO.setDeptId(user.getDeptId());
    userVO.setDeptName(user.getDeptName());
    userVO.setSex(user.getSex());
    if (user.getPassword() != null) {
      userVO.setPassword("******"); // 将密码字段设置为掩码, 以保护用户隐私
    }
    if (user.getEmail() != null) {
      userVO.setEmail(user.getEmail().replaceAll("(^[^@]{3}|(?!^)\\G)[^@]", "$1*")); // 将邮箱字段的用户名部分设置为掩码, 以保护用户隐私
    }
    return userVO;
  }
}
```
新增“数据库映射实体”必须：
- `extends ink.icoding.smartmybatis.entity.po.PO`
- 表：`@TableName`（import：`ink.icoding.smartmybatis.entity.po.enums.TableName`）
- 主键：`@ID`（import：`ink.icoding.smartmybatis.entity.po.enums.ID`）
- 字段：`@TableField`（import：`ink.icoding.smartmybatis.entity.po.enums.TableField`）
    - 非表字段：`@TableField(exist = false)`
    - JSON 字段：`@TableField(json = true)`（按 SmartMybatis 参数）
    - 默认长度为255, 需要修改长度时, 直接在`@TableField`中设置`length`参数即可, 例如：`@TableField(value = "USER_NAME", description = "用户名", length=50)`
    - 当`json = true`时, 将使用`longtext`存储, 此时默认长度参数将失效, 因为`longtext`类型没有长度限制.

`@TableName` 支持初始化脚本(表结构会根据实体自动创建或更新, 除非要初始化一些初始的记录, 否则不需要init参数)：

```text
@TableName(value = "china_cities", description = "中国省市区表", init = "smart-mybatis/init-citys.sql")
```
自动联表查询:
```text
// 在 XxxEntity 中定义了一个关联查询字段 extName, 关联了 Classify 表的 name 字段, 关联条件是本表的 extId 字段等于 Classify 表的 id 字段. 那么在查询 XxxEntity 时, 就可以直接使用这个关联查询字段, 无需手动写联表查询条件. 例如:
xxxEntity.selectWithRelations(Where where);
```

新增 Mapper 必须：
- `@Mapper`
- `extends ink.icoding.smartmybatis.mapper.base.SmartMapper<T>`

模板：

```text
@Mapper
public interface XxxMapper extends SmartMapper<XxxEntity> {
}
```

不建议直接在Service中直接写复杂的查询(查询条件超过2个, 或带有联表的), 复杂的查询建议在 Mapper 写“数据访问封装”的 `default` 方法（不要写业务规则），示例（`DeptNameMapMapper` 风格）：
import ink.icoding.smartmybatis.entity.expression.Where;
```text
default List<DeptNameMap> listByNames(Collection<String> names) {
    return this.select(Where.where(DeptNameMap::getDelFlag).eq("0")
        .and(DeptNameMap::getStatus).eq("0")
        .and(DeptNameMap::getDeptName).in(names)
    );
}
```
```text
以下代码:
if (dto != null) {
    if (dto.getName() != null && !dto.getName().trim().isEmpty()) {
        where.and(Group::getName).like(dto.getName().trim());
    }
    if (dto.getCreateBy() != null) {
        where.and(Group::getCreateBy).eq(dto.getCreateBy());
    }
    if (dto.getCreateTimeFrom() != null) {
        where.and(Group::getCreateTime).gt(dto.getCreateTimeFrom());
    }
}
可以简化为:
if (dto != null) {
    where.ifAnd(Group::getName).like(dto.getName())
        .ifAnd(Group::getCreateBy).eq(dto.getCreateBy())
        .ifAnd(Group::getCreateTime).gt(dto.getCreateTimeFrom().getTime())
}
```
Mapper初始化时, 会自动创建/更新表结构, 无需手动建表, 若需要初始化表数据(比如默认用户), 则在实体类的`@TableName`注解上初始化.
### 其他:
空条件查询: Where.where();
公共的Mapper方法:
- int insert(@Param("record") T record);
- int insertBatch(@Param("list") Collection<T> records);
- List<T> selectAll();
- List<T> select(Where where);
- List<T> selectWithRelations(Where where);
- long count();
- long count(Where where);
- T selectById(Serializable id);
- T selectOne(Where where);
- T selectFirst(Where where);
- PageResult<T> selectPage(Where where, Page page);
- List<Map<String, Object>> queryBySql(String sql, Object... params);
- int deleteById(Serializable id);
- deleteByIds(@Param("ids") Collection<? extends Serializable> ids);
- int delete(Where where);
- int updateById(@Param("record") T record);
- int executeSql(String sql, Object... params);
- int executeSqlScript(String script); // 支持多条SQL语句执行
  其中, ink.icoding.smartmybatis.entity.Page:
```text
public class Page {
    private int page;
    private int pageSize;
    public Page(int page, int pageSize) {
        this.page = page;
        this.pageSize = pageSize;
    }
    // getter setter
}
```
其中, ink.icoding.smartmybatis.entity.PageResult的结构为:
```text
public class PageResult<T> {
    private List<T> data;
    private long total;
    private int page;
    private int pageSize;
    private int totalPages;

    public PageResult(List<T> data, long total, int page, int pageSize) {
        this.data = data;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
        this.totalPages = (int) Math.ceil((double) total / pageSize);
    }
    // getter
}
```
手动联表查询(假设有Order表, 关联了User表, 需要查询订单列表和订单对应的用户名称), 那么Order表实体应如下:
```text
@TableName("order")
public class Order {
    @ID
    private Long id;
    private Long userId;
    private String product;

    /**
     * 外键关联字段, 其中:
     * - `exist = false` 表示该字段不对应数据库表中的列, 仅用于关联查询时存储关联数据。
     * - `link = User.class` 指定关联的实体类为 `User`
     * - `linkField = "name"` 指定关联的字段为 `User` 实体类中的 `name` 字段。
     */
    @TableField(exist = false, link = User.class, linkField = "name")
    private String userName;
    // Getter和Setter方法
}
```
然后在OrderMapper中写一个联表查询的方法:
```text
List<Order> orders = orderMapper.select(Where.where().leftJoin(
        User.class,
        "u",
        Where.where(User::getId).eq(Order::getUserId),
        Order::getUserName
));
```
如果需要联表查询过滤,先把用户名字联出来, 再在主 Where 里做过滤:
```text
Where joinOn = Where.where(User::getId).eq(Order::getUserId);

List<Order> orders = orderMapper.select(Where.where()
        .leftJoin(User.class, "u", joinOn, Order::getUserName)
        .and(Order::getUserName).like("张")
);
```
多表联表查询示例:
1. 拆解每个联表条件
```text
Where joinUser = Where.where(User::getId).eq(Order::getUserId);
Where joinItem = Where.where(OrderItem::getOrderId).eq(Order::getId);
Where joinProduct = Where.where(Product::getId).eq(OrderItem::getProductId);
```
2. 串联多个 leftJoin
```text
List<Order> orders = orderMapper.select(Where.where()
        .leftJoin(User.class, "u", joinUser, Order::getUserName)
        .leftJoin(OrderItem.class, "oi", joinItem)
        .leftJoin(Product.class, "p", joinProduct, Order::getProductName)
);
```

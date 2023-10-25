package org.btbox.demo.mapper;

import org.btbox.common.mybatis.annotation.DataColumn;
import org.btbox.common.mybatis.annotation.DataPermission;
import org.btbox.common.mybatis.core.mapper.BaseMapperPlus;
import org.btbox.demo.domain.TestTree;
import org.btbox.demo.domain.vo.TestTreeVo;

/**
 * 测试树表Mapper接口
 *
 * @author Lion Li
 * @date 2021-07-26
 */
@DataPermission({
    @DataColumn(key = "deptName", value = "dept_id"),
    @DataColumn(key = "userName", value = "user_id")
})
public interface TestTreeMapper extends BaseMapperPlus<TestTree, TestTreeVo> {

}

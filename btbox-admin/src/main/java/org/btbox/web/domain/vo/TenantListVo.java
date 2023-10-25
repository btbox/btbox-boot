package org.btbox.web.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.btbox.system.domain.vo.SysTenantVo;

/**
 * 租户列表
 *
 * @author Lion Li
 */
@Data
@AutoMapper(target = SysTenantVo.class)
public class TenantListVo {

    private String tenantId;

    private String companyName;

    private String domain;

}

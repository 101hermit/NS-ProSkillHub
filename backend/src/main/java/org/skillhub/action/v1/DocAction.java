package org.skillhub.action.v1;

import java.util.List;
import org.skillhub.business.CatalogService;
import org.skillhub.common.ApiResult;
import org.skillhub.dto.SiteDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 在线文档端点：目录 + 正文。 */
@RestController
@RequestMapping("/api/v1/docs")
public class DocAction {

    private final CatalogService catalogService;

    public DocAction(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public ApiResult<List<SiteDtos.DocItem>> catalog() {
        return ApiResult.ok(catalogService.docs());
    }

    @GetMapping("/{docKey}")
    public ApiResult<SiteDtos.DocDetail> page(@PathVariable("docKey") String docKey) {
        return ApiResult.ok(catalogService.doc(docKey));
    }
}

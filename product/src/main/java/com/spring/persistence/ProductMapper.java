package com.spring.persistence;

import com.spring.domain.Product;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * @Description 产品Mapper
 * @Author ErnestCheng
 * @Date 2017/5/27.
 */
@Repository
public interface ProductMapper {
    /**
     * 添加产品
     * @param product
     */
    void addProduct(Product product);

    /**
     * 获得产品
     * @param productId
     * @return
     */
    Product getProductById(@Param("productId") Integer productId);

    /**
     * 获得所有产品
     * @return
     */
    List<Product> queryProductList();
    /**
     * 更新产品库存
     * @param productId
     * @param num
     * @return
     */
    int updateProductStock(@Param("productId") Integer productId,@Param("num") Integer num);

    /**
     * 更新产品信息
     * @param productId
     * @param productName
     * @param stock
     * @param price
     * @return
     */
    int updateProduct(@Param("productId") Integer productId, @Param("productName")String productName, @Param("stock")Integer stock, @Param("price")BigDecimal price);

    /**
     * 删除产品
     * @param productId
     * @return
     */
    int deleteProductByProductId(@Param("productId") Integer productId);
}

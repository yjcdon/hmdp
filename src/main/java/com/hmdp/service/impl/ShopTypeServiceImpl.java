package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.MyConvertUtils;
import com.sun.deploy.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.hmdp.constants.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate srt;

    @Autowired
    private ShopTypeMapper shopTypeMapper;


    @Override
    public List<ShopType> getByCacheOrList () {
        // 先从Redis中获取
        List<String> cacheTypes = srt.opsForList().range(CACHE_SHOP_TYPE_KEY, 0, -1);

        // 如果得到的不是空列表
        if (!cacheTypes.isEmpty()) {
            // 拼接成标准JSON数组字符串
            String json = "[" + StringUtils.join(cacheTypes, ",") + "]";
            // 批量解析为对象列表
            return JSONUtil.toList(json, ShopType.class);
        }

        // 在Redis中没找到，去数据库查
        LambdaQueryWrapper<ShopType> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByAsc(ShopType::getSort);
        List<ShopType> shopTypeList = shopTypeMapper.selectList(queryWrapper);

        // 放入Redis中
        srt.opsForList().rightPushAll(CACHE_SHOP_TYPE_KEY,
                MyConvertUtils.objectListToStringList(shopTypeList, type -> JSONUtil.toJsonStr(type)));

        // if (shopTypeList != null) {
        //     for (ShopType shopType : shopTypeList) {
        //         // 源头放入Redis的格式就不能错，如果你直接把list中的对象放进Redis，它会要求变为String类型
        //         // 而toString之后，就很难变成JSON了；所以要先转为JSON再放入Redis
        //         String jsonStr = JSONUtil.toJsonStr(shopType);
        //         srt.opsForList().rightPush(CACHE_SHOP_TYPE_KEY, jsonStr);
        //     }
        //     return shopTypeList;
        // }

        // 查不到，返回null
        return null;
    }
}

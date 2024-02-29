package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.result.Result;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.MyRedisUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.hmdp.constants.RedisConstants.SECKILL_STOCK_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Autowired
    private SeckillVoucherMapper seckillVoucherMapper;

    @Autowired
    private VoucherMapper voucherMapper;

    @Autowired
    private MyRedisUtils redisUtils;

    @Autowired
    private VoucherOrderMapper voucherOrderMapper;


    @Autowired
    private StringRedisTemplate srt;

    @Override
    public Result queryVoucherOfShop (Long shopId) {
        // 查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public void addSeckillVoucher (Voucher voucher) {
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherMapper.insert(seckillVoucher);

        // 保存到Redis
        // 保存库存到Redis，key是voucherid，value是库存数
        srt.opsForValue().set(SECKILL_STOCK_KEY + voucher.getId(), seckillVoucher.getStock().toString());

    }
}

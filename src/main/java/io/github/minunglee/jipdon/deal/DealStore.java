package io.github.minunglee.jipdon.deal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import io.github.minunglee.jipdon.collect.DealKind;
import io.github.minunglee.jipdon.collect.RtmsItem;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 거래 저장. 같은 달을 여러 번 다시 받아도 결과가 같도록(멱등) 거래 내용의 해시를 고유 키로 쓴다.
 * 매매는 나중에 "계약 해제"로 바뀌어 다시 올 수 있어, 같은 거래면 해제 여부만 갱신한다.
 */
@Repository
public class DealStore {

	private final JdbcTemplate jdbc;

	DealStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** 한 지역·종류의 건물 id를 미리 읽어, 페이지마다 건물을 하나씩 조회하지 않게 한다 */
	public Map<BuildingKey, Long> loadBuildings(BuildingKind kind, String sggCd) {
		Map<BuildingKey, Long> ids = new HashMap<>();
		jdbc.query("SELECT id, umd_nm, jibun, name FROM building WHERE kind = ? AND sgg_cd = ?",
				rs -> {
					ids.put(new BuildingKey(kind, sggCd, rs.getString("umd_nm"), rs.getString("jibun"),
							rs.getString("name")), rs.getLong("id"));
				}, kind.name(), sggCd);
		return ids;
	}

	/**
	 * 한 페이지의 거래를 저장한다.
	 * @return 새로 들어가거나 해제 여부가 바뀐 거래 수
	 */
	@Transactional
	public int save(DealKind kind, String sggCd, List<RtmsItem> items, Map<BuildingKey, Long> buildingIds) {
		List<Object[]> rows = new ArrayList<>(items.size());
		for (RtmsItem item : items) {
			if (item.exclusiveArea() == null || (kind.sale() ? item.price() == null : item.deposit() == null)) {
				continue; // 면적이나 금액이 빠진 거래는 계산에 쓸 수 없다
			}
			BuildingKey key = new BuildingKey(kind.buildingKind(), sggCd, item.umd(), item.jibun(), item.buildingName());
			long buildingId = buildingIds.computeIfAbsent(key, k -> insertBuilding(k, item));
			rows.add(kind.sale()
					? new Object[] { buildingId, item.exclusiveArea(), item.floor(), Date.valueOf(item.dealDate()),
							item.price(), item.canceled(), naturalKey(kind, key, item) }
					: new Object[] { buildingId, item.exclusiveArea(), item.floor(), Date.valueOf(item.dealDate()),
							item.deposit(), item.monthlyRent(), item.contractType(), naturalKey(kind, key, item) });
		}
		if (rows.isEmpty()) {
			return 0;
		}
		int[] counts = kind.sale() ? jdbc.batchUpdate("""
				INSERT INTO sale_deal (building_id, exclusive_area, floor, deal_date, price_man, canceled, natural_key)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (natural_key) DO UPDATE SET canceled = EXCLUDED.canceled
				WHERE sale_deal.canceled IS DISTINCT FROM EXCLUDED.canceled
				""", rows) : jdbc.batchUpdate("""
				INSERT INTO rent_deal (building_id, exclusive_area, floor, deal_date, deposit_man, monthly_rent_man,
				                       contract_type, natural_key)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (natural_key) DO NOTHING
				""", rows);
		int changed = 0;
		for (int count : counts) {
			changed += Math.max(count, 0);
		}
		return changed;
	}

	/**
	 * 단독·다가구 거래 저장 (건물 없이 동 단위). 매매는 계약 해제 여부만 갱신한다.
	 * @return 새로 들어가거나 해제 여부가 바뀐 거래 수
	 */
	@Transactional
	public int saveDetached(DealKind kind, String sggCd, List<RtmsItem> items) {
		List<Object[]> rows = new ArrayList<>(items.size());
		for (RtmsItem item : items) {
			if (item.totalFloorArea() == null || (kind.sale() ? item.price() == null : item.deposit() == null)) {
				continue;
			}
			rows.add(new Object[] { sggCd, item.umd(), item.houseType(), kind.sale() ? "SALE" : "RENT",
					item.totalFloorArea(), item.plottageArea(), item.buildYear(), Date.valueOf(item.dealDate()),
					item.price(), item.deposit(), item.monthlyRent(), item.canceled(), detachedKey(kind, sggCd, item) });
		}
		if (rows.isEmpty()) {
			return 0;
		}
		int[] counts = jdbc.batchUpdate("""
				INSERT INTO detached_deal (sgg_cd, umd_nm, house_type, deal_type, total_floor_area, plottage_area,
				                           build_year, deal_date, price_man, deposit_man, monthly_rent_man, canceled,
				                           natural_key)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (natural_key) DO UPDATE SET canceled = EXCLUDED.canceled
				WHERE detached_deal.canceled IS DISTINCT FROM EXCLUDED.canceled
				""", rows);
		int changed = 0;
		for (int count : counts) {
			changed += Math.max(count, 0);
		}
		return changed;
	}

	/** 단독·다가구 거래의 해시. 지번은 가려진 값("1*") 그대로 넣는다 */
	static String detachedKey(DealKind kind, String sggCd, RtmsItem item) {
		return hash(String.join("|", kind.name(), sggCd, item.umd(), item.jibun(), String.valueOf(item.houseType()),
				item.totalFloorArea().stripTrailingZeros().toPlainString(),
				item.plottageArea() == null ? "" : item.plottageArea().stripTrailingZeros().toPlainString(),
				String.valueOf(item.buildYear()), item.dealDate().toString(), String.valueOf(item.price()),
				String.valueOf(item.deposit()), String.valueOf(item.monthlyRent())));
	}

	/** 처음 보는 건물을 저장하고 id를 받는다. 이미 있으면 기존 id (준공연도는 비어 있을 때만 채움) */
	private long insertBuilding(BuildingKey key, RtmsItem item) {
		// 다른 수집이 먼저 넣었을 수 있으므로 충돌하면 기존 id를 돌려받는다
		Long id = jdbc.queryForObject("""
				INSERT INTO building (kind, sgg_cd, umd_nm, jibun, name, house_type, build_year)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (kind, sgg_cd, umd_nm, jibun, name)
				DO UPDATE SET build_year = COALESCE(building.build_year, EXCLUDED.build_year)
				RETURNING id
				""", Long.class, key.kind().name(), key.sggCd(), key.umd(), key.jibun(), key.name(), item.houseType(),
				item.buildYear());
		return id;
	}

	/**
	 * 거래 내용의 해시(128비트). 해제 여부는 넣지 않는다 — 같은 거래가 해제로 바뀌어 다시 와도 같은 키가 되게.
	 */
	static String naturalKey(DealKind kind, BuildingKey key, RtmsItem item) {
		String raw = String.join("|", kind.name(), key.sggCd(), key.umd(), key.jibun(), key.name(),
				item.exclusiveArea().stripTrailingZeros().toPlainString(), String.valueOf(item.floor()),
				item.dealDate().toString(), String.valueOf(item.price()), String.valueOf(item.deposit()),
				String.valueOf(item.monthlyRent()), String.valueOf(item.contractType()));
		return hash(raw);
	}

	/** SHA-256 해시 앞 16바이트(32자). 거래 고유 키로 쓴다 */
	private static String hash(String raw) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash, 0, 16);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

/*
 * Kontalk Android client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.kontalk.R;
import org.kontalk.client.NumberValidator;

import android.content.Context;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.i18n.phonenumbers.PhoneNumberUtil;


public class CountryCodesAdapter extends BaseAdapter {

    /**
     * Static mappings of region codes to flags drawable.
     * Don't you really think this was written manually? NAAAAHH!!! :P
     */
    private static final SparseIntArray sFlags = new SparseIntArray(233);
    static {
        sFlags.put(0x4146 /* AF */, R.drawable.flag_af);
        sFlags.put(0x414c /* AL */, R.drawable.flag_al);
        sFlags.put(0x445a /* DZ */, R.drawable.flag_dz);
        sFlags.put(0x4144 /* AD */, R.drawable.flag_ad);
        sFlags.put(0x414f /* AO */, R.drawable.flag_ao);
        sFlags.put(0x4149 /* AI */, R.drawable.flag_ai);
        sFlags.put(0x4147 /* AG */, R.drawable.flag_ag);
        sFlags.put(0x5341 /* SA */, R.drawable.flag_sa);
        sFlags.put(0x4152 /* AR */, R.drawable.flag_ar);
        sFlags.put(0x414d /* AM */, R.drawable.flag_am);
        sFlags.put(0x4157 /* AW */, R.drawable.flag_aw);
        sFlags.put(0x4155 /* AU */, R.drawable.flag_au);
        sFlags.put(0x4154 /* AT */, R.drawable.flag_at);
        sFlags.put(0x415a /* AZ */, R.drawable.flag_az);
        sFlags.put(0x4253 /* BS */, R.drawable.flag_bs);
        sFlags.put(0x4248 /* BH */, R.drawable.flag_bh);
        sFlags.put(0x4244 /* BD */, R.drawable.flag_bd);
        sFlags.put(0x4242 /* BB */, R.drawable.flag_bb);
        sFlags.put(0x4245 /* BE */, R.drawable.flag_be);
        sFlags.put(0x425a /* BZ */, R.drawable.flag_bz);
        sFlags.put(0x424a /* BJ */, R.drawable.flag_bj);
        sFlags.put(0x424d /* BM */, R.drawable.flag_bm);
        sFlags.put(0x4254 /* BT */, R.drawable.flag_bt);
        sFlags.put(0x4259 /* BY */, R.drawable.flag_by);
        sFlags.put(0x424f /* BO */, R.drawable.flag_bo);
        sFlags.put(0x4241 /* BA */, R.drawable.flag_ba);
        sFlags.put(0x4257 /* BW */, R.drawable.flag_bw);
        sFlags.put(0x4252 /* BR */, R.drawable.flag_br);
        sFlags.put(0x424e /* BN */, R.drawable.flag_bn);
        sFlags.put(0x4247 /* BG */, R.drawable.flag_bg);
        sFlags.put(0x4246 /* BF */, R.drawable.flag_bf);
        sFlags.put(0x4249 /* BI */, R.drawable.flag_bi);
        sFlags.put(0x4b48 /* KH */, R.drawable.flag_kh);
        sFlags.put(0x434d /* CM */, R.drawable.flag_cm);
        sFlags.put(0x4341 /* CA */, R.drawable.flag_ca);
        sFlags.put(0x4356 /* CV */, R.drawable.flag_cv);
        sFlags.put(0x5444 /* TD */, R.drawable.flag_td);
        sFlags.put(0x434c /* CL */, R.drawable.flag_cl);
        sFlags.put(0x434e /* CN */, R.drawable.flag_cn);
        sFlags.put(0x4359 /* CY */, R.drawable.flag_cy);
        sFlags.put(0x5641 /* VA */, R.drawable.flag_va);
        sFlags.put(0x434f /* CO */, R.drawable.flag_co);
        sFlags.put(0x4b4d /* KM */, R.drawable.flag_km);
        sFlags.put(0x4347 /* CG */, R.drawable.flag_cg);
        sFlags.put(0x4b50 /* KP */, R.drawable.flag_kp);
        sFlags.put(0x4b52 /* KR */, R.drawable.flag_kr);
        sFlags.put(0x4352 /* CR */, R.drawable.flag_cr);
        sFlags.put(0x4349 /* CI */, R.drawable.flag_ci);
        sFlags.put(0x4852 /* HR */, R.drawable.flag_hr);
        sFlags.put(0x4355 /* CU */, R.drawable.flag_cu);
        sFlags.put(0x444b /* DK */, R.drawable.flag_dk);
        sFlags.put(0x444d /* DM */, R.drawable.flag_dm);
        sFlags.put(0x4543 /* EC */, R.drawable.flag_ec);
        sFlags.put(0x4547 /* EG */, R.drawable.flag_eg);
        sFlags.put(0x5356 /* SV */, R.drawable.flag_sv);
        sFlags.put(0x4145 /* AE */, R.drawable.flag_ae);
        sFlags.put(0x4552 /* ER */, R.drawable.flag_er);
        sFlags.put(0x4545 /* EE */, R.drawable.flag_ee);
        sFlags.put(0x4554 /* ET */, R.drawable.flag_et);
        sFlags.put(0x464a /* FJ */, R.drawable.flag_fj);
        sFlags.put(0x5048 /* PH */, R.drawable.flag_ph);
        sFlags.put(0x4649 /* FI */, R.drawable.flag_fi);
        sFlags.put(0x4652 /* FR */, R.drawable.flag_fr);
        sFlags.put(0x4741 /* GA */, R.drawable.flag_ga);
        sFlags.put(0x474d /* GM */, R.drawable.flag_gm);
        sFlags.put(0x4745 /* GE */, R.drawable.flag_ge);
        sFlags.put(0x4445 /* DE */, R.drawable.flag_de);
        sFlags.put(0x4748 /* GH */, R.drawable.flag_gh);
        sFlags.put(0x4a4d /* JM */, R.drawable.flag_jm);
        sFlags.put(0x4a50 /* JP */, R.drawable.flag_jp);
        sFlags.put(0x4749 /* GI */, R.drawable.flag_gi);
        sFlags.put(0x444a /* DJ */, R.drawable.flag_dj);
        sFlags.put(0x4a4f /* JO */, R.drawable.flag_jo);
        sFlags.put(0x4752 /* GR */, R.drawable.flag_gr);
        sFlags.put(0x4744 /* GD */, R.drawable.flag_gd);
        sFlags.put(0x474c /* GL */, R.drawable.flag_gl);
        sFlags.put(0x4750 /* GP */, R.drawable.flag_gp);
        sFlags.put(0x4755 /* GU */, R.drawable.flag_gu);
        sFlags.put(0x4754 /* GT */, R.drawable.flag_gt);
        sFlags.put(0x4746 /* GF */, R.drawable.flag_gf);
        sFlags.put(0x474e /* GN */, R.drawable.flag_gn);
        sFlags.put(0x4757 /* GW */, R.drawable.flag_gw);
        sFlags.put(0x4751 /* GQ */, R.drawable.flag_gq);
        sFlags.put(0x4759 /* GY */, R.drawable.flag_gy);
        sFlags.put(0x4854 /* HT */, R.drawable.flag_ht);
        sFlags.put(0x484e /* HN */, R.drawable.flag_hn);
        sFlags.put(0x484b /* HK */, R.drawable.flag_hk);
        sFlags.put(0x494e /* IN */, R.drawable.flag_in);
        sFlags.put(0x4944 /* ID */, R.drawable.flag_id);
        sFlags.put(0x4952 /* IR */, R.drawable.flag_ir);
        sFlags.put(0x4951 /* IQ */, R.drawable.flag_iq);
        sFlags.put(0x4945 /* IE */, R.drawable.flag_ie);
        sFlags.put(0x4953 /* IS */, R.drawable.flag_is);
        sFlags.put(0x4e46 /* NF */, R.drawable.flag_nf);
        sFlags.put(0x4358 /* CX */, R.drawable.flag_cx);
        sFlags.put(0x4158 /* AX */, R.drawable.flag_ax);
        sFlags.put(0x4b59 /* KY */, R.drawable.flag_ky);
        sFlags.put(0x4343 /* CC */, R.drawable.flag_cc);
        sFlags.put(0x434b /* CK */, R.drawable.flag_ck);
        sFlags.put(0x464b /* FK */, R.drawable.flag_fk);
        sFlags.put(0x464f /* FO */, R.drawable.flag_fo);
        sFlags.put(0x4d50 /* MP */, R.drawable.flag_mp);
        sFlags.put(0x4d48 /* MH */, R.drawable.flag_mh);
        sFlags.put(0x5342 /* SB */, R.drawable.flag_sb);
        sFlags.put(0x5443 /* TC */, R.drawable.flag_tc);
        sFlags.put(0x5647 /* VG */, R.drawable.flag_vg);
        sFlags.put(0x5649 /* VI */, R.drawable.flag_vi);
        sFlags.put(0x494c /* IL */, R.drawable.flag_il);
        sFlags.put(0x4954 /* IT */, R.drawable.flag_it);
        sFlags.put(0x4b5a /* KZ */, R.drawable.flag_kz);
        sFlags.put(0x4b45 /* KE */, R.drawable.flag_ke);
        sFlags.put(0x4b47 /* KG */, R.drawable.flag_kg);
        sFlags.put(0x4b49 /* KI */, R.drawable.flag_ki);
        sFlags.put(0x4b57 /* KW */, R.drawable.flag_kw);
        sFlags.put(0x4c41 /* LA */, R.drawable.flag_la);
        sFlags.put(0x4c53 /* LS */, R.drawable.flag_ls);
        sFlags.put(0x4c56 /* LV */, R.drawable.flag_lv);
        sFlags.put(0x4c42 /* LB */, R.drawable.flag_lb);
        sFlags.put(0x4c52 /* LR */, R.drawable.flag_lr);
        sFlags.put(0x4c59 /* LY */, R.drawable.flag_ly);
        sFlags.put(0x4c49 /* LI */, R.drawable.flag_li);
        sFlags.put(0x4c54 /* LT */, R.drawable.flag_lt);
        sFlags.put(0x4c55 /* LU */, R.drawable.flag_lu);
        sFlags.put(0x4d4f /* MO */, R.drawable.flag_mo);
        sFlags.put(0x4d4b /* MK */, R.drawable.flag_mk);
        sFlags.put(0x4d47 /* MG */, R.drawable.flag_mg);
        sFlags.put(0x4d57 /* MW */, R.drawable.flag_mw);
        sFlags.put(0x4d59 /* MY */, R.drawable.flag_my);
        sFlags.put(0x4d56 /* MV */, R.drawable.flag_mv);
        sFlags.put(0x4d4c /* ML */, R.drawable.flag_ml);
        sFlags.put(0x4d54 /* MT */, R.drawable.flag_mt);
        sFlags.put(0x4d41 /* MA */, R.drawable.flag_ma);
        sFlags.put(0x4d51 /* MQ */, R.drawable.flag_mq);
        sFlags.put(0x4d52 /* MR */, R.drawable.flag_mr);
        sFlags.put(0x4d55 /* MU */, R.drawable.flag_mu);
        sFlags.put(0x5954 /* YT */, R.drawable.flag_yt);
        sFlags.put(0x4d58 /* MX */, R.drawable.flag_mx);
        sFlags.put(0x464d /* FM */, R.drawable.flag_fm);
        sFlags.put(0x4d44 /* MD */, R.drawable.flag_md);
        sFlags.put(0x4d43 /* MC */, R.drawable.flag_mc);
        sFlags.put(0x4d4e /* MN */, R.drawable.flag_mn);
        sFlags.put(0x4d45 /* ME */, R.drawable.flag_me);
        sFlags.put(0x4d53 /* MS */, R.drawable.flag_ms);
        sFlags.put(0x4d5a /* MZ */, R.drawable.flag_mz);
        sFlags.put(0x4d4d /* MM */, R.drawable.flag_mm);
        sFlags.put(0x4e41 /* NA */, R.drawable.flag_na);
        sFlags.put(0x4e52 /* NR */, R.drawable.flag_nr);
        sFlags.put(0x4e50 /* NP */, R.drawable.flag_np);
        sFlags.put(0x4e49 /* NI */, R.drawable.flag_ni);
        sFlags.put(0x4e45 /* NE */, R.drawable.flag_ne);
        sFlags.put(0x4e47 /* NG */, R.drawable.flag_ng);
        sFlags.put(0x4e55 /* NU */, R.drawable.flag_nu);
        sFlags.put(0x4e4f /* NO */, R.drawable.flag_no);
        sFlags.put(0x4e43 /* NC */, R.drawable.flag_nc);
        sFlags.put(0x4e5a /* NZ */, R.drawable.flag_nz);
        sFlags.put(0x4f4d /* OM */, R.drawable.flag_om);
        sFlags.put(0x4e4c /* NL */, R.drawable.flag_nl);
        sFlags.put(0x504b /* PK */, R.drawable.flag_pk);
        sFlags.put(0x5057 /* PW */, R.drawable.flag_pw);
        sFlags.put(0x5053 /* PS */, R.drawable.flag_ps);
        sFlags.put(0x5041 /* PA */, R.drawable.flag_pa);
        sFlags.put(0x5047 /* PG */, R.drawable.flag_pg);
        sFlags.put(0x5059 /* PY */, R.drawable.flag_py);
        sFlags.put(0x5045 /* PE */, R.drawable.flag_pe);
        sFlags.put(0x5046 /* PF */, R.drawable.flag_pf);
        sFlags.put(0x504c /* PL */, R.drawable.flag_pl);
        sFlags.put(0x5054 /* PT */, R.drawable.flag_pt);
        sFlags.put(0x5052 /* PR */, R.drawable.flag_pr);
        sFlags.put(0x5141 /* QA */, R.drawable.flag_qa);
        sFlags.put(0x4742 /* GB */, R.drawable.flag_gb);
        sFlags.put(0x435a /* CZ */, R.drawable.flag_cz);
        sFlags.put(0x4346 /* CF */, R.drawable.flag_cf);
        sFlags.put(0x444f /* DO */, R.drawable.flag_do);
        sFlags.put(0x4344 /* CD */, R.drawable.flag_cd);
        sFlags.put(0x5245 /* RE */, R.drawable.flag_re);
        sFlags.put(0x524f /* RO */, R.drawable.flag_ro);
        sFlags.put(0x5257 /* RW */, R.drawable.flag_rw);
        sFlags.put(0x5255 /* RU */, R.drawable.flag_ru);
        sFlags.put(0x4548 /* EH */, R.drawable.flag_eh);
        sFlags.put(0x4b4e /* KN */, R.drawable.flag_kn);
        sFlags.put(0x504d /* PM */, R.drawable.flag_pm);
        sFlags.put(0x5643 /* VC */, R.drawable.flag_vc);
        sFlags.put(0x4c43 /* LC */, R.drawable.flag_lc);
        sFlags.put(0x5753 /* WS */, R.drawable.flag_ws);
        sFlags.put(0x4153 /* AS */, R.drawable.flag_as);
        sFlags.put(0x534d /* SM */, R.drawable.flag_sm);
        sFlags.put(0x5348 /* SH */, R.drawable.flag_sh);
        sFlags.put(0x534e /* SN */, R.drawable.flag_sn);
        sFlags.put(0x5253 /* RS */, R.drawable.flag_rs);
        sFlags.put(0x5343 /* SC */, R.drawable.flag_sc);
        sFlags.put(0x534c /* SL */, R.drawable.flag_sl);
        sFlags.put(0x5347 /* SG */, R.drawable.flag_sg);
        sFlags.put(0x5359 /* SY */, R.drawable.flag_sy);
        sFlags.put(0x534b /* SK */, R.drawable.flag_sk);
        sFlags.put(0x5349 /* SI */, R.drawable.flag_si);
        sFlags.put(0x534f /* SO */, R.drawable.flag_so);
        sFlags.put(0x4553 /* ES */, R.drawable.flag_es);
        sFlags.put(0x4c4b /* LK */, R.drawable.flag_lk);
        sFlags.put(0x5553 /* US */, R.drawable.flag_us);
        sFlags.put(0x5a41 /* ZA */, R.drawable.flag_za);
        sFlags.put(0x5344 /* SD */, R.drawable.flag_sd);
        sFlags.put(0x5352 /* SR */, R.drawable.flag_sr);
        sFlags.put(0x534a /* SJ */, R.drawable.flag_sj);
        sFlags.put(0x5345 /* SE */, R.drawable.flag_se);
        sFlags.put(0x4348 /* CH */, R.drawable.flag_ch);
        sFlags.put(0x535a /* SZ */, R.drawable.flag_sz);
        sFlags.put(0x5354 /* ST */, R.drawable.flag_st);
        sFlags.put(0x544a /* TJ */, R.drawable.flag_tj);
        sFlags.put(0x5457 /* TW */, R.drawable.flag_tw);
        sFlags.put(0x545a /* TZ */, R.drawable.flag_tz);
        sFlags.put(0x494f /* IO */, R.drawable.flag_io);
        sFlags.put(0x5448 /* TH */, R.drawable.flag_th);
        sFlags.put(0x544c /* TL */, R.drawable.flag_tl);
        sFlags.put(0x5447 /* TG */, R.drawable.flag_tg);
        sFlags.put(0x544b /* TK */, R.drawable.flag_tk);
        sFlags.put(0x544f /* TO */, R.drawable.flag_to);
        sFlags.put(0x5454 /* TT */, R.drawable.flag_tt);
        sFlags.put(0x544e /* TN */, R.drawable.flag_tn);
        sFlags.put(0x5452 /* TR */, R.drawable.flag_tr);
        sFlags.put(0x544d /* TM */, R.drawable.flag_tm);
        sFlags.put(0x5456 /* TV */, R.drawable.flag_tv);
        sFlags.put(0x5541 /* UA */, R.drawable.flag_ua);
        sFlags.put(0x5547 /* UG */, R.drawable.flag_ug);
        sFlags.put(0x4855 /* HU */, R.drawable.flag_hu);
        sFlags.put(0x5559 /* UY */, R.drawable.flag_uy);
        sFlags.put(0x555a /* UZ */, R.drawable.flag_uz);
        sFlags.put(0x5655 /* VU */, R.drawable.flag_vu);
        sFlags.put(0x5645 /* VE */, R.drawable.flag_ve);
        sFlags.put(0x564e /* VN */, R.drawable.flag_vn);
        sFlags.put(0x5746 /* WF */, R.drawable.flag_wf);
        sFlags.put(0x5945 /* YE */, R.drawable.flag_ye);
        sFlags.put(0x5a4d /* ZM */, R.drawable.flag_zm);
        sFlags.put(0x5a57 /* ZW */, R.drawable.flag_zw);
    }

    private final LayoutInflater mInflater;
    private final List<CountryCode> mData;
    private final int mViewId;
    private final int mDropdownViewId;
    private int mSelected;

    public static final class CountryCode implements Comparable<String> {
        public String regionCode;
        public int countryCode;
        public String regionName;

        @Override
        public int compareTo(String another) {
            return regionCode != null && another != null ? regionCode.compareTo(another) : 0;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;

            if (o != null && o instanceof CountryCode) {
                CountryCode other = (CountryCode) o;

                if (regionCode != null && regionCode.equals(other.regionCode))
                    return true;

                return (countryCode == other.countryCode);
            }

            return false;
        }

        @Override
        public String toString() {
            return regionCode;
        }
    }

    public CountryCodesAdapter(Context context, int viewId, int dropdownViewId) {
        this(context, new ArrayList<CountryCode>(), viewId, dropdownViewId);
    }

    public CountryCodesAdapter(Context context, List<CountryCode> data, int viewId, int dropdownViewId) {
        mInflater = LayoutInflater.from(context);
        mData = data;
        mViewId = viewId;
        mDropdownViewId = dropdownViewId;
    }

    public void add(CountryCode entry) {
        mData.add(entry);
    }

    public void add(String regionCode) {
        CountryCode cc = new CountryCode();
        cc.regionCode = regionCode;
        cc.countryCode = PhoneNumberUtil.getInstance().getCountryCodeForRegion(regionCode);
        cc.regionName = NumberValidator.getRegionDisplayName(regionCode, Locale.getDefault());
        mData.add(cc);
    }

    public void clear() {
        mData.clear();
    }

    public void sort(Comparator<? super CountryCode> comparator) {
        Collections.sort(mData, comparator);
    }

    @Override
    public int getCount() {
        return mData.size();
    }

    @Override
    public Object getItem(int position) {
        return mData.get(position);
    }

    @Override
    public long getItemId(int position) {
        CountryCode e = mData.get(position);
        return (e != null) ? e.countryCode : -1;
    }

    public int getPositionForId(CountryCode cc) {
        Log.d("CC", "looking for region " + cc);
        return cc != null ? mData.indexOf(cc) : -1;
    }

    public void setSelected(int position) {
        mSelected = position;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        DropDownViewHolder holder;
        View view;
        if (convertView == null) {
            view = mInflater.inflate(mDropdownViewId, null, false);
            holder = new DropDownViewHolder();
            holder.icon = (ImageView) view.findViewById(android.R.id.icon);
            holder.description = (CheckedTextView) view.findViewById(android.R.id.text1);
            view.setTag(holder);
        }
        else {
            view = convertView;
            holder = (DropDownViewHolder) view.getTag();
        }

        CountryCode e = mData.get(position);

        StringBuilder text = new StringBuilder(5)
            .append(e.regionName)
            .append(" (+")
            .append(e.countryCode)
            .append(')');

        holder.description.setText(text);
        holder.description.setChecked((mSelected == position));

        int flagId = getFlag(e.regionCode);
        if (flagId > 0)
            holder.icon.setImageResource(flagId);
        else
            holder.icon.setImageDrawable(null);

        return view;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        View view;
        if (convertView == null) {
            view = mInflater.inflate(mViewId, null, false);
            holder = new ViewHolder();
            holder.icon = (ImageView) view.findViewById(android.R.id.icon);
            holder.description = (TextView) view.findViewById(android.R.id.text1);
            view.setTag(holder);
        }
        else {
            view = convertView;
            holder = (ViewHolder) view.getTag();
        }

        CountryCode e = mData.get(position);

        StringBuilder text = new StringBuilder(3)
            .append('+')
            .append(e.countryCode)
            .append(" (")
            .append(e.regionName)
            .append(')');

        holder.description.setText(text);

        int flagId = getFlag(e.regionCode);
        if (flagId > 0)
            holder.icon.setImageResource(flagId);
        else
            holder.icon.setImageDrawable(null);

        return view;
    }

    private int getFlag(String regionCode) {
        char[] b = regionCode.toCharArray();
        short key = (short) ((short) (b[0] << 8) + (short) b[1]);
        return sFlags.get(key);
    }

    private final static class ViewHolder {
        TextView description;
        ImageView icon;
    }

    private final static class DropDownViewHolder {
        CheckedTextView description;
        ImageView icon;
    }
}

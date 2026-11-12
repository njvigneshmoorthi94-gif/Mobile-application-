import React, { useState, useEffect, useCallback } from 'react';
import {
  SafeAreaView, View, Text, TextInput, TouchableOpacity,
  ScrollView, StyleSheet, Alert, Linking, RefreshControl, Switch, StatusBar
} from 'react-native';

const API_URL = 'http://192.168.1.10:3000'; // Replace with your Server IP or Cloud HTTPS URL

export default function App() {
  const [tab, setTab] = useState('STOCK');
  const [products, setProducts] = useState([]);
  const [orders, setOrders] = useState([]);
  const [dailySales, setDailySales] = useState([]);
  const [refreshing, setRefreshing] = useState(false);

  const [pName, setPName] = useState('');
  const [pSku, setPSku] = useState('');
  const [pPrice, setPPrice] = useState('');
  const [pStock, setPStock] = useState('');

  const [custName, setCustName] = useState('');
  const [custPhone, setCustPhone] = useState('');
  const [cart, setCart] = useState({});
  const [payStatus, setPayStatus] = useState('PAID');
  const [payMethod, setPayMethod] = useState('UPI');
  const [autoWaMode, setAutoWaMode] = useState(true);
  const [directWaMode, setDirectWaMode] = useState(true);

  const fetchAll = useCallback(async () => {
    setRefreshing(true);
    try {
      const [pRes, oRes, sRes] = await Promise.all([
        fetch(`${API_URL}/api/products`),
        fetch(`${API_URL}/api/orders`),
        fetch(`${API_URL}/api/sales/daily`)
      ]);
      setProducts(await pRes.json());
      setOrders(await oRes.json());
      setDailySales(await sRes.json());
    } catch (e) {
      Alert.alert('Connection Error', 'Ensure Thennaval backend server is running and API_URL is reachable.');
    }
    setRefreshing(false);
  }, []);

  useEffect(() => {
    fetchAll();
  }, [fetchAll]);

  const adjustStock = async (id, delta) => {
    await fetch(`${API_URL}/api/products/${id}/stock`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ delta })
    });
    fetchAll();
  };

  const addProduct = async () => {
    if (!pName || !pPrice || !pStock) return Alert.alert('Missing Fields', 'Enter name, price, and stock.');
    await fetch(`${API_URL}/api/products`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: pName,
        sku: pSku || `SKU-${Date.now().toString().slice(-4)}`,
        price: Number(pPrice),
        stock: Number(pStock),
        low_stock_threshold: 5
      })
    });
    setPName(''); setPSku(''); setPPrice(''); setPStock('');
    fetchAll();
  };

  const openCustomerWhatsApp = (phone, message) => {
    const clean = phone.replace(/\D/g, '');
    const fullPhone = clean.startsWith('91') ? clean : `91${clean}`;
    const url = `https://wa.me/${fullPhone}?text=${encodeURIComponent(message)}`;
    Linking.openURL(url).catch(() => Alert.alert('Error', 'WhatsApp is not installed on this device.'));
  };

  const submitOrder = async () => {
    const selectedItems = products
      .filter(p => (cart[p.id] || 0) > 0)
      .map(p => ({ product_id: p.id, name: p.name, qty: cart[p.id], price: p.price }));

    if (!custName || !custPhone || selectedItems.length === 0) {
      return Alert.alert('Incomplete Order', 'Enter customer details and select at least 1 product.');
    }

    const res = await fetch(`${API_URL}/api/orders`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        customer_name: custName,
        customer_phone: custPhone,
        items: selectedItems,
        payment_status: payStatus,
        payment_method: payMethod,
        send_auto_wa: autoWaMode
      })
    });
    const data = await res.json();

    const summary = selectedItems.map(i => `• ${i.name} x${i.qty} = ₹${i.qty * i.price}`).join('\n');
    const waText = `Vanakkam ${custName}!\nOrder #${data.id} confirmed with Thennaval:\n${summary}\nTotal: ₹${data.total_amount}\nPayment: ${payStatus} (${payMethod})`;

    if (directWaMode) {
      openCustomerWhatsApp(custPhone, waText);
    }

    setCustName(''); setCustPhone(''); setCart({});
    fetchAll();
    setTab('ORDERS');
  };

  const markOrderPaid = async (order) => {
    await fetch(`${API_URL}/api/orders/${order.id}/pay`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ amount: order.total_amount, method: 'UPI', send_auto_wa: true })
    });
    fetchAll();
  };

  const inStockCount = products.filter(p => p.status === 'IN_STOCK').length;
  const lowStockCount = products.filter(p => p.status === 'LOW_STOCK').length;
  const outStockCount = products.filter(p => p.status === 'OUT_OF_STOCK').length;

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar backgroundColor="#0F5132" barStyle="light-content" />
      <View style={styles.header}>
        <Text style={styles.headerTitle}>THENNAVAL AI</Text>
        <Text style={styles.headerSub}>Owner Auto-Alerts: ON • Customer WA: Dual Mode</Text>
      </View>

      <View style={styles.navBar}>
        {['STOCK', 'NEW_ORDER', 'ORDERS', 'SALES'].map(t => (
          <TouchableOpacity
            key={t}
            style={[styles.navBtn, tab === t && styles.navBtnActive]}
            onPress={() => setTab(t)}
          >
            <Text style={[styles.navText, tab === t && styles.navTextActive]}>
              {t.replace('_', ' ')}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      <ScrollView
        style={styles.body}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={fetchAll} />}
      >
        {tab === 'STOCK' && (
          <View>
            <View style={styles.kpiRow}>
              <View style={[styles.kpiBox, { backgroundColor: '#D1E7DD' }]}>
                <Text style={styles.kpiNum}>{inStockCount}</Text>
                <Text style={styles.kpiLbl}>In Stock</Text>
              </View>
              <View style={[styles.kpiBox, { backgroundColor: '#FFF3CD' }]}>
                <Text style={styles.kpiNum}>{lowStockCount}</Text>
                <Text style={styles.kpiLbl}>Low Stock</Text>
              </View>
              <View style={[styles.kpiBox, { backgroundColor: '#F8D7DA' }]}>
                <Text style={styles.kpiNum}>{outStockCount}</Text>
                <Text style={styles.kpiLbl}>Out of Stock</Text>
              </View>
            </View>

            <View style={styles.card}>
              <Text style={styles.cardTitle}>Add New Stock Item</Text>
              <TextInput style={styles.input} placeholder="Product Name" value={pName} onChangeText={setPName} />
              <View style={styles.row}>
                <TextInput style={[styles.input, { flex: 1, marginRight: 6 }]} placeholder="SKU" value={pSku} onChangeText={setPSku} />
                <TextInput style={[styles.input, { flex: 1, marginRight: 6 }]} placeholder="Price ₹" keyboardType="numeric" value={pPrice} onChangeText={setPPrice} />
                <TextInput style={[styles.input, { flex: 1 }]} placeholder="Qty" keyboardType="numeric" value={pStock} onChangeText={setPStock} />
              </View>
              <TouchableOpacity style={styles.primaryBtn} onPress={addProduct}>
                <Text style={styles.primaryBtnText}>+ Save Product</Text>
              </TouchableOpacity>
            </View>

            {products.map(p => (
              <View key={p.id} style={styles.card}>
                <View style={styles.rowBetween}>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.itemTitle}>{p.name}</Text>
                    <Text style={styles.itemSub}>SKU: {p.sku} • ₹{p.price}</Text>
                  </View>
                  <View style={[
                    styles.badge,
                    p.status === 'OUT_OF_STOCK' ? styles.badgeRed :
                    p.status === 'LOW_STOCK' ? styles.badgeYellow : styles.badgeGreen
                  ]}>
                    <Text style={styles.badgeText}>{p.status.replace(/_/g, ' ')} ({p.stock})</Text>
                  </View>
                </View>

                <View style={[styles.row, { marginTop: 10 }]}>
                  <TouchableOpacity style={styles.stepBtn} onPress={() => adjustStock(p.id, -1)}>
                    <Text style={styles.stepBtnText}>-1</Text>
                  </TouchableOpacity>
                  <TouchableOpacity style={styles.stepBtn} onPress={() => adjustStock(p.id, 1)}>
                    <Text style={styles.stepBtnText}>+1</Text>
                  </TouchableOpacity>
                  <TouchableOpacity style={styles.stepBtn} onPress={() => adjustStock(p.id, 10)}>
                    <Text style={styles.stepBtnText}>+10 Restock</Text>
                  </TouchableOpacity>
                </View>
              </View>
            ))}
          </View>
        )}

        {tab === 'NEW_ORDER' && (
          <View style={styles.card}>
            <Text style={styles.cardTitle}>Create Customer Order</Text>
            <TextInput style={styles.input} placeholder="Customer Name" value={custName} onChangeText={setCustName} />
            <TextInput style={styles.input} placeholder="WhatsApp Number (e.g. 9876543210)" keyboardType="phone-pad" value={custPhone} onChangeText={setCustPhone} />

            <Text style={styles.sectionLabel}>Select Items:</Text>
            {products.map(p => (
              <View key={p.id} style={styles.rowBetween}>
                <Text style={{ flex: 1 }}>{p.name} (₹{p.price}) [{p.stock} left]</Text>
                <View style={styles.row}>
                  <TouchableOpacity
                    style={styles.miniBtn}
                    onPress={() => setCart({ ...cart, [p.id]: Math.max(0, (cart[p.id] || 0) - 1) })}
                  >
                    <Text>-</Text>
                  </TouchableOpacity>
                  <Text style={{ marginHorizontal: 10, fontWeight: 'bold' }}>{cart[p.id] || 0}</Text>
                  <TouchableOpacity
                    style={styles.miniBtn}
                    onPress={() => {
                      if ((cart[p.id] || 0) < p.stock) {
                        setCart({ ...cart, [p.id]: (cart[p.id] || 0) + 1 });
                      }
                    }}
                  >
                    <Text>+</Text>
                  </TouchableOpacity>
                </View>
              </View>
            ))}

            <Text style={styles.sectionLabel}>Payment Status & Method:</Text>
            <View style={styles.row}>
              {['PAID', 'PENDING'].map(st => (
                <TouchableOpacity
                  key={st}
                  style={[styles.chip, payStatus === st && styles.chipActive]}
                  onPress={() => setPayStatus(st)}
                >
                  <Text style={payStatus === st ? { color: '#fff' } : {}}>{st}</Text>
                </TouchableOpacity>
              ))}
              {['UPI', 'CASH', 'CARD'].map(m => (
                <TouchableOpacity
                  key={m}
                  style={[styles.chip, payMethod === m && styles.chipActive]}
                  onPress={() => setPayMethod(m)}
                >
                  <Text style={payMethod === m ? { color: '#fff' } : {}}>{m}</Text>
                </TouchableOpacity>
              ))}
            </View>

            <View style={styles.switchRow}>
              <Text>Mode A: Auto Cloud API WhatsApp</Text>
              <Switch value={autoWaMode} onValueChange={setAutoWaMode} />
            </View>
            <View style={styles.switchRow}>
              <Text>Mode B: Open WhatsApp App Chat</Text>
              <Switch value={directWaMode} onValueChange={setDirectWaMode} />
            </View>

            <TouchableOpacity style={styles.primaryBtn} onPress={submitOrder}>
              <Text style={styles.primaryBtnText}>Confirm Order & Send Alerts</Text>
            </TouchableOpacity>
          </View>
        )}

        {tab === 'ORDERS' && orders.map(o => (
          <View key={o.id} style={styles.card}>
            <View style={styles.rowBetween}>
              <Text style={styles.itemTitle}>Order #{o.id} • {o.customer_name}</Text>
              <Text style={{ fontWeight: 'bold', color: o.payment_status === 'PAID' ? '#198754' : '#DC3545' }}>
                {o.payment_status} (₹{o.total_amount})
              </Text>
            </View>
            <Text style={styles.itemSub}>{o.customer_phone} • {o.created_at}</Text>
            {o.items.map((it, idx) => (
              <Text key={idx} style={{ fontSize: 13, marginTop: 2 }}>• {it.name} x{it.qty} = ₹{it.qty * it.price}</Text>
            ))}

            <View style={[styles.row, { marginTop: 10 }]}>
              {o.payment_status !== 'PAID' && (
                <TouchableOpacity style={[styles.stepBtn, { backgroundColor: '#198754' }]} onPress={() => markOrderPaid(o)}>
                  <Text style={{ color: '#fff', fontWeight: 'bold' }}>Mark Paid (UPI)</Text>
                </TouchableOpacity>
              )}
              <TouchableOpacity
                style={[styles.stepBtn, { backgroundColor: '#25D366' }]}
                onPress={() => openCustomerWhatsApp(
                  o.customer_phone,
                  `Hi ${o.customer_name}, update on Thennaval Order #${o.id}: Amount ₹${o.total_amount} (${o.payment_status}).`
                )}
              >
                <Text style={{ color: '#fff', fontWeight: 'bold' }}>WhatsApp Customer</Text>
              </TouchableOpacity>
            </View>
          </View>
        ))}

        {tab === 'SALES' && (
          <View>
            {dailySales.map((d, idx) => (
              <View key={idx} style={styles.card}>
                <View style={styles.rowBetween}>
                  <Text style={styles.itemTitle}>{d.sale_date}</Text>
                  <Text style={styles.itemTitle}>₹{d.gross_sales}</Text>
                </View>
                <Text style={styles.itemSub}>Total Orders: {d.total_orders}</Text>
                <View style={[styles.rowBetween, { marginTop: 8 }]}>
                  <Text style={{ color: '#198754', fontWeight: 'bold' }}>Collected: ₹{d.collected_amount}</Text>
                  <Text style={{ color: '#DC3545', fontWeight: 'bold' }}>Pending: ₹{d.pending_amount}</Text>
                </View>
              </View>
            ))}
          </View>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F4F6F8' },
  header: { backgroundColor: '#0F5132', padding: 16 },
  headerTitle: { color: '#fff', fontSize: 20, fontWeight: 'bold' },
  headerSub: { color: '#D1E7DD', fontSize: 12, marginTop: 2 },
  navBar: { flexDirection: 'row', backgroundColor: '#fff', borderBottomWidth: 1, borderColor: '#ddd' },
  navBtn: { flex: 1, paddingVertical: 12, alignItems: 'center' },
  navBtnActive: { borderBottomWidth: 3, borderColor: '#0F5132' },
  navText: { fontSize: 12, fontWeight: '600', color: '#666' },
  navTextActive: { color: '#0F5132', fontWeight: 'bold' },
  body: { padding: 12 },
  kpiRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 12 },
  kpiBox: { flex: 1, padding: 12, borderRadius: 8, marginHorizontal: 4, alignItems: 'center' },
  kpiNum: { fontSize: 20, fontWeight: 'bold', color: '#111' },
  kpiLbl: { fontSize: 12, color: '#333' },
  card: { backgroundColor: '#fff', padding: 14, borderRadius: 10, marginBottom: 12, elevation: 2 },
  cardTitle: { fontSize: 16, fontWeight: 'bold', marginBottom: 10, color: '#0F5132' },
  input: { borderWidth: 1, borderColor: '#ccc', borderRadius: 6, padding: 10, marginBottom: 8, backgroundColor: '#FAFAFA' },
  row: { flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap' },
  rowBetween: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginVertical: 4 },
  primaryBtn: { backgroundColor: '#0F5132', padding: 12, borderRadius: 8, alignItems: 'center', marginTop: 8 },
  primaryBtnText: { color: '#fff', fontWeight: 'bold', fontSize: 15 },
  itemTitle: { fontSize: 15, fontWeight: 'bold', color: '#222' },
  itemSub: { fontSize: 12, color: '#666', marginTop: 2 },
  badge: { paddingHorizontal: 8, paddingVertical: 4, borderRadius: 6 },
  badgeGreen: { backgroundColor: '#D1E7DD' },
  badgeYellow: { backgroundColor: '#FFF3CD' },
  badgeRed: { backgroundColor: '#F8D7DA' },
  badgeText: { fontSize: 11, fontWeight: 'bold' },
  stepBtn: { backgroundColor: '#E9ECEF', paddingHorizontal: 12, paddingVertical: 6, borderRadius: 6, marginRight: 8 },
  stepBtnText: { fontWeight: 'bold', color: '#222' },
  sectionLabel: { fontWeight: 'bold', marginTop: 10, marginBottom: 6 },
  miniBtn: { borderWidth: 1, borderColor: '#aaa', width: 28, height: 28, alignItems: 'center', justifyContent: 'center', borderRadius: 4 },
  chip: { borderWidth: 1, borderColor: '#0F5132', paddingHorizontal: 10, paddingVertical: 6, borderRadius: 16, marginRight: 6, marginBottom: 6 },
  chipActive: { backgroundColor: '#0F5132' },
  switchRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginVertical: 6 }
});

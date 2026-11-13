
import React, {useEffect, useState} from 'react';
import {
  SafeAreaView, View, Text, TextInput,
  TouchableOpacity, ScrollView, Alert, StyleSheet
} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';

const KEY = 'THENNAVAL_DATA';

const initial = {
  products: [],
  sales: [],
  payments: [],
  purchases: [],
  customers: [],
  expenses: []
};

const money = n => '₹' + Number(n || 0).toFixed(2);

export default function App() {
  const [data, setData] = useState(initial);
  const [page, setPage] = useState('Home');
  const [ready, setReady] = useState(false);

  const [name, setName] = useState('');
  const [qty, setQty] = useState('1');
  const [buy, setBuy] = useState('');
  const [sell, setSell] = useState('');
  const [customer, setCustomer] = useState('');
  const [phone, setPhone] = useState('');
  const [paid, setPaid] = useState('');
  const [total, setTotal] = useState('');

  useEffect(() => {
    AsyncStorage.getItem(KEY).then(value => {
      if (value) setData({...initial, ...JSON.parse(value)});
      setReady(true);
    }).catch(() => setReady(true));
  }, []);

  useEffect(() => {
    if (ready) AsyncStorage.setItem(KEY, JSON.stringify(data));
  }, [data, ready]);

  const save = (key, value) => {
    setData(old => ({...old, [key]: value}));
  };

  const today = new Date().toISOString().slice(0, 10);

  const todaySales = data.sales.filter(s => s.date === today);
  const salesTotal = todaySales.reduce((a, s) => a + s.total, 0);
  const profit = todaySales.reduce((a, s) => a + s.profit, 0);
  const lowStock = data.products.filter(p => p.qty <= p.min);

  const addProduct = () => {
    if (!name.trim() || Number(qty) < 0) {
      return Alert.alert('Enter valid product details');
    }

    const product = {
      id: Date.now().toString(),
      name: name.trim(),
      qty: Number(qty),
      buy: Number(buy || 0),
      sell: Number(sell || 0),
      min: 2
    };

    save('products', [...data.products, product]);
    setName('');
    setQty('1');
    setBuy('');
    setSell('');
    Alert.alert('Success', 'Product added');
  };

  const addPurchase = () => {
    if (!name.trim() || Number(qty) < 1) {
      return Alert.alert('Enter product and quantity');
    }

    const existing = data.products.find(
      p => p.name.toLowerCase() === name.trim().toLowerCase()
    );

    if (existing) {
      save('products', data.products.map(p =>
        p.id === existing.id
          ? {...p, qty: p.qty + Number(qty)}
          : p
      ));
    } else {
      save('products', [...data.products, {
        id: Date.now().toString(),
        name: name.trim(),
        qty: Number(qty),
        buy: Number(buy || 0),
        sell: Number(sell || 0),
        min: 2
      }]);
    }

    save('purchases', [{
      id: Date.now().toString(),
      date: today,
      name,
      qty: Number(qty),
      amount: Number(buy || 0) * Number(qty)
    }, ...data.purchases]);

    Alert.alert('Purchase recorded');
  };

  const recordSale = (product) => {
    Alert.alert('Sell product', 'Choose quantity', [
      {text: 'Cancel'},
      {
        text: 'Sell 1',
        onPress: () => {
          if (product.qty < 1) {
            return Alert.alert('Out of stock');
          }

          const sale = {
            id: Date.now().toString(),
            date: today,
            product: product.name,
            qty: 1,
            total: product.sell,
            profit: product.sell - product.buy
          };

          save('products', data.products.map(p =>
            p.id === product.id
              ? {...p, qty: p.qty - 1}
              : p
          ));

          save('sales', [sale, ...data.sales]);
          Alert.alert('Sale completed');
        }
      }
    ]);
  };

  const addPayment = () => {
    const t = Number(total);
    const p = Number(paid);

    if (!customer.trim() || t <= 0 || p < 0 || p > t) {
      return Alert.alert('Check payment details');
    }

    save('payments', [{
      id: Date.now().toString(),
      date: today,
      customer,
      phone,
      total: t,
      paid: p,
      balance: t - p
    }, ...data.payments]);

    setCustomer('');
    setPhone('');
    setTotal('');
    setPaid('');
    Alert.alert('Payment saved');
  };

  const field = (value, change, placeholder, numeric = false) => (
    <TextInput
      style={styles.input}
      value={value}
      onChangeText={change}
      placeholder={placeholder}
      placeholderTextColor="#9ca3af"
      keyboardType={numeric ? 'numeric' : 'default'}
    />
  );

  const button = (label, action) => (
    <TouchableOpacity style={styles.button} onPress={action}>
      <Text style={styles.buttonText}>{label}</Text>
    </TouchableOpacity>
  );

  const card = (label, value) => (
    <View style={styles.card}>
      <Text style={styles.muted}>{label}</Text>
      <Text style={styles.value}>{value}</Text>
    </View>
  );

  const row = (title, value) => (
    <View style={styles.row}>
      <Text style={styles.white}>{title}</Text>
      <Text style={styles.white}>{value}</Text>
    </View>
  );

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.brand}>THENNAVAL AI</Text>
        <Text style={styles.muted}>Spare Parts Management</Text>
      </View>

      <ScrollView style={styles.content}>

        {page === 'Home' && <>
          <Text style={styles.heading}>Dashboard</Text>
          <View style={styles.grid}>
            {card("Today's Sales", money(salesTotal))}
            {card("Today's Profit", money(profit))}
            {card("Total Products", data.products.length)}
            {card("Low Stock", lowStock.length)}
            {card("Sales Count", todaySales.length)}
            {card("Payments", data.payments.length)}
          </View>

          <Text style={styles.heading}>Stock Alerts</Text>
          {lowStock.map(p => row(
            p.name,
            p.qty === 0 ? 'EMPTY' : `LOW: ${p.qty}`
          ))}
        </>}

        {page === 'Stock' && <>
          <Text style={styles.heading}>Stock Management</Text>
          {field(name, setName, 'Product name')}
          {field(qty, setQty, 'Quantity', true)}
          {field(buy, setBuy, 'Purchase price', true)}
          {field(sell, setSell, 'Selling price', true)}
          {button('Add Product', addProduct)}
          {button('Record Purchase', addPurchase)}

          <Text style={styles.heading}>Available Products</Text>
          {data.products.map(p => (
            <View style={styles.card} key={p.id}>
              <Text style={styles.white}>{p.name}</Text>
              <Text style={styles.muted}>Stock: {p.qty}</Text>
              <Text style={styles.muted}>
                Buy {money(p.buy)} | Sell {money(p.sell)}
              </Text>
              {button('Sell 1', () => recordSale(p))}
            </View>
          ))}
        </>}

        {page === 'Orders' && <>
          <Text style={styles.heading}>Sales History</Text>
          {data.sales.map(s => row(
            `${s.product} × ${s.qty}`,
            money(s.total)
          ))}
        </>}

        {page === 'Payments' && <>
          <Text style={styles.heading}>Payment Management</Text>
          {field(customer, setCustomer, 'Customer name')}
          {field(phone, setPhone, 'WhatsApp number')}
          {field(total, setTotal, 'Bill amount', true)}
          {field(paid, setPaid, 'Amount paid', true)}
          {button('Save Payment', addPayment)}

          <Text style={styles.heading}>Payment Records</Text>
          {data.payments.map(p => (
            <View style={styles.card} key={p.id}>
              <Text style={styles.white}>{p.customer}</Text>
              <Text style={styles.muted}>
                Total: {money(p.total)}
              </Text>
              <Text style={styles.muted}>
                Paid: {money(p.paid)}
              </Text>
              <Text style={styles.alert}>
                Balance: {money(p.balance)}
              </Text>
            </View>
          ))}
        </>}

        {page === 'Reports' && <>
          <Text style={styles.heading}>Business Reports</Text>
          {card('Today Sales', money(salesTotal))}
          {card('Today Profit', money(profit))}
          {card('Total Stock Units',
            data.products.reduce((a, p) => a + p.qty, 0))}
          {card('Purchase Entries', data.purchases.length)}
          {card('Payment Entries', data.payments.length)}
        </>}

        {page === 'More' && <>
          <Text style={styles.heading}>Business Management</Text>
          {button('Customers', () => Alert.alert(
            'Customer management',
            'Customer records are available through payment entries.'
          ))}
          {button('Suppliers', () => Alert.alert(
            'Supplier management',
            'Supplier module needs backend implementation.'
          ))}
          {button('Expenses', () => Alert.alert(
            'Expenses',
            'Expense module needs implementation.'
          ))}
          {button('WhatsApp', () => Alert.alert(
            'WhatsApp integration',
            'Official WhatsApp Cloud API setup is required.'
          ))}
        </>}

      </ScrollView>

      <View style={styles.navigation}>
        {['Home', 'Stock', 'Orders', 'Payments', 'Reports', 'More'].map(t => (
          <TouchableOpacity key={t} onPress={() => setPage(t)}>
            <Text style={[
              styles.navText,
              page === t && styles.active
            ]}>{t}</Text>
          </TouchableOpacity>
        ))}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {flex: 1, backgroundColor: '#0b1220'},
  header: {padding: 20, backgroundColor: '#14243c'},
  brand: {fontSize: 23, color: '#fff', fontWeight: '900'},
  content: {padding: 15},
  heading: {
    color: '#fff', fontSize: 20, fontWeight: 'bold',
    marginTop: 15, marginBottom: 12
  },
  grid: {flexDirection: 'row', flexWrap: 'wrap'},
  card: {
    backgroundColor: '#172a44', padding: 15,
    borderRadius: 12, margin: 4, flexGrow: 1, minWidth: '45%'
  },
  value: {fontSize: 21, color: '#fff', fontWeight: 'bold', marginTop: 8},
  muted: {color: '#9caec5', marginTop: 5},
  white: {color: '#fff', fontWeight: 'bold'},
  alert: {color: '#ff7979', marginTop: 5},
  input: {
    backgroundColor: '#172a44', color: '#fff',
    padding: 14, borderRadius: 10, marginBottom: 10
  },
  button: {
    backgroundColor: '#2563eb', padding: 13,
    borderRadius: 10, marginVertical: 5
  },
  buttonText: {color: '#fff', textAlign: 'center', fontWeight: 'bold'},
  row: {
    backgroundColor: '#172a44', padding: 14,
    marginBottom: 8, borderRadius: 10,
    flexDirection: 'row', justifyContent: 'space-between'
  },
  navigation: {
    flexDirection: 'row', justifyContent: 'space-around',
    padding: 12, backgroundColor: '#14243c'
  },
  navText: {color: '#9caec5', fontSize: 10, fontWeight: 'bold'},
  active: {color: '#60a5fa'}
});

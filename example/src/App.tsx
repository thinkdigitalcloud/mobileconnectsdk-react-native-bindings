import React, { useEffect, useState } from 'react';
import { Image, Button, View, Text, FlatList, StyleSheet, StatusBar } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { createStackNavigator } from '@react-navigation/stack';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import CredentialsScreen from './CredentialsScreen';
import ReadersScreen from './ReadersScreen';

import GallagherMobileAccess from '../../bindings/react-native-gallagher-mobile-access';

const styles = StyleSheet.create({
  container: {
    flex: 1,
    marginTop: StatusBar.currentHeight || 0,
  },
  listItem: {
    height: 44+8,
    backgroundColor: 'white',
    paddingVertical: 8,
    paddingHorizontal: 16,
    borderBottomColor: '#e9e9ec',
    borderBottomWidth: 1,
    justifyContent: 'center'
  },
});

function SaltoScreen({ navigation }) {
  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
      <Text>Salto</Text>
      <Button
        title="More Details"
        onPress={() => navigation.push('Details')} />
    </View>
  );
}

function DigitalIdScreen({ navigation }) {
  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
      <Text>Digital Id</Text>
      <Button
        title="More Details"
        onPress={() => navigation.push('Details')} />
    </View>
  );
}

const Tab = createBottomTabNavigator();

GallagherMobileAccess.configure(null, "allowInvalidCertificate", []);

function App() {
  useEffect(async () => {
    GallagherMobileAccess.setScanning(true);
    GallagherMobileAccess.setAutomaticAccessEnabled(true);

    const states = await GallagherMobileAccess.getStates();
    console.log("sdk states:" + JSON.stringify(states));
  }, []);

  return (
    <NavigationContainer>
      <Tab.Navigator
            screenOptions={({ route }) => ({
              tabBarIcon: ({ focused, color, size }) => {
                var icon;
                if (route.name === 'Credentials') {
                  icon = require('../img/outline_content_copy_black_48dp.png');
                } else if (route.name === 'Readers') {
                  icon = require('../img/outline_phonelink_ring_black_48dp.png');
                } else if (route.name === 'Salto') {
                  icon = require('../img/outline_class_black_48dp.png');
                } else if (route.name === 'Digital ID') {
                  icon = require('../img/outline_account_circle_black_48dp.png');
                }
                return <Image style={{ width: size, height: size, tintColor: color}} source={icon} />;
              },
            })}>
        <Tab.Screen name="Credentials" component={CredentialsScreen} />
        <Tab.Screen name="Readers" component={ReadersScreen} />
        <Tab.Screen name="Salto" component={SaltoScreen} />
        <Tab.Screen name="Digital ID" component={DigitalIdScreen} />
      </Tab.Navigator>
    </NavigationContainer>
  );
}

export default App;
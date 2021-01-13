import React from 'react';
import './App.css';
import logo from './cyclotrack-app.svg';
import { Flexbox, Text, Link, Icon, Spacer } from 'kvl-react-ui';
import { Link as RouterLink } from 'react-router-dom';

function Home() {
  return (
    <Flexbox
      flexDirection="column"
      backgroundColor="#282c34"
      color="#eee"
      padding="1em 1em 0 1em"
      height="100%"
      minHeight="100vh"
    >
      <Flexbox flexDirection="column" maxWidth="80ch" height="100%">
        <Link
          className="App-link"
          href="https://play.google.com/store/apps/details?id=com.kvl.cyclotrack"
          target="_blank"
          rel="noopener noreferrer"
          color="#669e58"
        >
          <Flexbox flexDirection="column" alignItems="center">
            <img width="50%" src={logo} alt="logo" />
            <Text fontSize="4em">Cyclotrack</Text>
            <Spacer height="2em" />
            <Text>Download Cyclotrack from the Google Play Store</Text>
          </Flexbox>
        </Link>
        <Spacer height="3em" />
        <h3>Become a beta tester</h3>
        <Link
          color="#669e58"
          className="App-link"
          href="https://play.google.com/apps/testing/com.kvl.cyclotrack"
        >
          Join beta testing from the web
        </Link>
        <Link
          color="#669e58"
          className="App-link"
          href="https://play.google.com/store/apps/details?id=com.kvl.cyclotrack"
        >
          Join beta testing from Google Play on Android
        </Link>
        <Spacer height="2em" />
        <RouterLink
          color="#669e58"
          className="App-link"
          to="/privacy"
          href="/privacy"
        >
          View Privacy policy
        </RouterLink>
      </Flexbox>
    </Flexbox>
  );
}

export default Home;
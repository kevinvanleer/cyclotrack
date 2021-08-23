import React from 'react';
import './App.css';
import logo from './cyclotrack-app.svg';
import summary from './screenshot-summary.jpg';
import dash from './screenshot-dash-ble.jpg';
import detailsTop from './screenshot-details-1.jpg';
import detailsTwo from './screenshot-details-2.jpg';
import detailsThree from './screenshot-details-3.jpg';
import detailsFour from './screenshot-details-4.jpg';
import { DonateButton } from './components/DonateButton';
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
        <Flexbox
          flexDirection="row"
          justifyContent="center"
          marginBetween="30px"
        >
          <img width="25%" src={summary} alt="summary view screenshot" />
          <img width="25%" src={dash} alt="dashboard screenshot" />
          <img width="25%" src={detailsTop} alt="details screenshot" />
        </Flexbox>
        <Spacer height="3em" />
        I started Cyclotrack because I could not find a free cycle computer app
        that provided an always-on dashboard with relevant performance data. I
        also wanted to build an app that was functional using only onboard
        sensors, primarily GPS. And finally, I wanted a record of my past rides
        so I could track progress toward my goals and compare with past
        performance.
        <Spacer height="1em" />
        As I developed and tested Cyclotrack, the shortcomings of smartphone GPS
        became apparent. The relatively low speed of cycling (compared to
        driving), shaded bike paths, buildings, and even cloudy days seemed to
        make tracking speed frustrating and unreliable. As a result I added
        support for Bluetooth Low Engery (BLE) heart rate, speed, and cadence
        sensors. This added a new level of fidelity to the data available to
        Cyclotrack, providing a much more reliable source for speed data and
        adding the ability to track pedaling cadence. Tracking heart rate
        provided a missing piece of information crucial to understanding how
        much effort I was expending on my rides. Over time, I was able to
        identify my optimal heart rate for pushing myself without burning out.
        <Spacer height="1em" />
        I wanted to have rich data analysis without relying on cloud services or
        third-party integrations with platforms like Strava or Google Fit, so
        Cyclotrack provides detailed ride analysis with, maps, graphs, and stats
        for each ride. This data is stored efficiently on your device, and can
        be exported to XLSX format so you can do your own analysis in your
        favorite spreadsheet application. Cyclotrack is fully functional
        offline, no network connection is required to utilize any of its
        features. Your data is safe. I do not collect any data or analytics
        (although I may collect anonymous app usage data in the future) and I do
        not send or sell any data to third parties.
        <Spacer height="1em" />
        Having said all that, I wanted to track all my fitness activity in a
        single experience, so I built an integration between Cyclotrack and
        Google Fit. This integration enables Cyclotrack to send data to Google
        Fit so that my rides contribute to my heart points, calories, miles, and
        active minutes. Cyclotrack rides also appear in the Google Fit journal.
        There was another reason I wanted this integration. Until this point I
        had been entering my weight in Google Fit for a historical record, and
        Cyclotrack so that my weight could be used to help estimate calorie burn
        on my rides. I updated Cyclotrack to read all relevant (and available)
        biometric data from Google Fit. Cyclotrack reads height, weight, and
        resting heart rate data from Google Fit. It uses this information, along
        with BLE heart rate data (if available), to estimate calorie burn. The
        best part is that this feature is totally optional. Cyclotrack is 100%
        functional even if you do not use Google Fit.
        <Spacer height="3em" />
        <Flexbox
          flexDirection="row"
          justifyContent="center"
          marginBetween="10px"
        >
          <img width="23%" src={detailsTop} alt="details screenshot with map" />
          <img
            width="23%"
            src={detailsTwo}
            alt="details screenshot with graphs"
          />
          <img
            width="23%"
            src={detailsThree}
            alt="details screenshot with splits"
          />
          <img
            width="23%"
            src={detailsFour}
            alt="details screenshot with elevation chart"
          />
        </Flexbox>
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
        <h3>Support Cyclotrack development</h3>
        <DonateButton />
        <Spacer height="2em" />
        <Link
          color="#669e58"
          className="App-link"
          href="https://github.com/kevinvanleer/cyclotrack"
        >
          Check out the source code on GitHub
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
        <Spacer height="2em" />
      </Flexbox>
    </Flexbox>
  );
}

export default Home;

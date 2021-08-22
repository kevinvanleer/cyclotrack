import React from 'react';
import './App.css';
import logo from './cyclotrack-app.svg';
import { Flexbox, Text, Link, Icon, Spacer } from 'kvl-react-ui';
import { Link as RouterLink } from 'react-router-dom';

function Privacy() {
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
        <RouterLink to="/">
          <img width="20%" src={logo} alt="logo" />
        </RouterLink>
        <Spacer height="3em" />
        <h3>Privacy Policy</h3>
        Cyclotrack uses location data from GPS receiver on a user device to
        visualize speed, distance, and map a route. All data collected by
        Cyclotrack is stored on the device. None of your location or fitness
        data is transmitted from the device to the developer of Cyclotrack. No
        data is transmitted to a third party unless you explicitly enable a
        third party integration (e.g. Google Fit).
        <h4>Google Fit</h4>A user may choose to link Cyclotrack with a Google
        Fit account. This is done by signing in to a Google account in
        Cyclotrack settings. When linked Cyclotrack will:
        <ul>
          <li>
            Read height, weight, and resting heart rate stored in Google Fit.
            This data will be used to assist in calculating metrics like calorie
            burn. This data will be stored in Cyclotrack as part of a ride. The
            data will persist until the ride is deleted from Cyclotrack.
          </li>
          <li>
            Send data to Google Fit to be entered in the activity journal and
            used to track toward Google Fit goals. These include: location,
            distance, speed, cadence, and heart rate.
          </li>
        </ul>
        No data received from Google Fit will be shared with or transmitted to
        any third party.
        <Spacer height="1em" />
        <span>
          <i>
            <b>Disclosure:</b> Use and transfer to any other app of information
            received from Google APIs by Cyclotrack will adhere to the{' '}
            <Link href="https://developers.google.com/terms/api-services-user-data-policy">
              Google API Services User Data Policy
            </Link>
            , including the Limited Use requirements.
          </i>
        </span>
        <h4>Google Analytics</h4>Cyclotrack uses Google Analytics to help
        understand user behavior and improve the overall user experience. The
        Advertising ID and the Secure Settings Android ID (SSAID) are not
        collected. You may opt out of analytics collection at any time by
        disabling &quot;Data collection&quot; in app settings.
        <Spacer height="2em" />
      </Flexbox>
    </Flexbox>
  );
}

export default Privacy;

/*
 * This code and all components (c) Copyright 2006 - 2016, Wowza Media Systems, LLC. All rights reserved.
 * This code is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 */
package com.wowza.wms.plugin;

import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import com.wowza.util.StringUtils;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.client.IClient;
import com.wowza.wms.httpstreamer.model.IHTTPStreamerSession;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.logging.WMSLoggerIDs;
import com.wowza.wms.module.ModuleBase;
import com.wowza.wms.rtp.model.RTPSession;

public class ModuleTimedDisconnect extends ModuleBase
{

	private class Disconnecter extends TimerTask
	{

		public synchronized void run()
		{
			Iterator<IClient> clients = appInstance.getClients().iterator();
			while (clients.hasNext())
			{
				IClient client = clients.next();
				if (client.getTimeRunningSeconds() > disconnectTime)
				{
					if (checkAllowedIPAddress(client.getIp()) && checkAllowedUserAgent(client.getFlashVer()))
					{
						if (debugLog)
							logger.info(MODULE_NAME + ": RTMP disconnecting client " + client.getClientId() + " FlashVer is " + client.getFlashVer(), WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);

						client.setShutdownClient(true);
					}
				}
			}

			Iterator<IHTTPStreamerSession> httpSessions = appInstance.getHTTPStreamerSessions().iterator();
			while (httpSessions.hasNext())
			{
				IHTTPStreamerSession httpSession = httpSessions.next();
				if (httpSession.getTimeRunningSeconds() > disconnectTime)
				{
					if (checkAllowedIPAddress(httpSession.getIpAddress()) && checkAllowedUserAgent(httpSession.getUserAgent()))
					{
						if (debugLog)
							logger.info(MODULE_NAME + ": HTTP disconnecting session " + httpSession.getSessionId(), WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);

						httpSession.rejectSession();
					}
				}
			}

			Iterator<RTPSession> rtpSessions = appInstance.getRTPSessions().iterator();
			while (rtpSessions.hasNext())
			{
				RTPSession rtpSession = rtpSessions.next();
				if (rtpSession.getTimeRunningSeconds() > disconnectTime)
				{
					if (checkAllowedIPAddress(rtpSession.getIp()) && checkAllowedUserAgent(rtpSession.getUserAgent()))
					{
						if (debugLog)
							logger.info(MODULE_NAME + ": RTSP disconnecting client " + rtpSession.getSessionId(), WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);

						appInstance.getVHost().getRTPContext().shutdownRTPSession(rtpSession);
					}
				}
			}
		}
	}

	public static final String MODULE_NAME = "ModuleTimedDisconnect";
	public static final String PROP_NAME_PREFIX = "timedDisconnect";
	
	private IApplicationInstance appInstance = null;
	private Timer timer = null;
	private int disconnectTime = 60;
	private String allowedIps = "";
	private String allowedAgents = "";
	private boolean debugLog = false;
	private WMSLogger logger = null;

	public void onAppStart(IApplicationInstance appInstance)
	{
		this.appInstance = appInstance;
		this.logger = WMSLoggerFactory.getLoggerObj(appInstance);
		this.disconnectTime = appInstance.getProperties().getPropertyInt(PROP_NAME_PREFIX + "Time", this.disconnectTime);
		this.allowedIps = appInstance.getProperties().getPropertyStr(PROP_NAME_PREFIX + "AllowedIPs", this.allowedIps);
		this.allowedAgents = appInstance.getProperties().getPropertyStr(PROP_NAME_PREFIX + "AllowedAgents", this.allowedAgents);
		this.debugLog = appInstance.getProperties().getPropertyBoolean(PROP_NAME_PREFIX + "DebugLog", this.debugLog);
		if (logger.isDebugEnabled())
		{
			this.debugLog = true;
		}
		
		logger.info(MODULE_NAME + " Started  [" + appInstance.getContextStr() + " disconnectTime: "+ this.disconnectTime + ", ignoredAgents: " + this.allowedAgents + ", debugLog: " + this.debugLog + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);

		this.timer = new Timer(MODULE_NAME + " [" + appInstance.getContextStr() + "]");
		timer.schedule(new Disconnecter(), 0, 1000);
	}

	public void onAppStop(IApplicationInstance appInstance)
	{
		logger.info(MODULE_NAME + ": Stopped [" + appInstance.getContextStr() + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);

		if (timer != null)
		{
			timer.cancel();
		}
		timer = null;
	}

	private boolean checkAllowedIPAddress(String ip)
	{
		if(ip.equalsIgnoreCase("127.0.0.1"))
		{
			if(this.debugLog)
				getLogger().info(MODULE_NAME + ": IP will be ignored [" + ip + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
			return false;
		}
		
		String[] allowedIpArray = allowedIps.toLowerCase().split(",");
		if (allowedIpArray != null)
		{
			for (int i = 0; i < allowedIpArray.length; i++)
			{
				String allowedIP = allowedIpArray[i].trim();
				if (StringUtils.isEmpty(allowedIP))
					continue;
				
				if (ip.toLowerCase().startsWith(allowedIP))
				{
					if(this.debugLog)
						getLogger().info(MODULE_NAME + ": IP will be ignored [" + ip + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
					return false;
				}
			}
		}
		if(this.debugLog)
			getLogger().info(MODULE_NAME + ": IP will be checked [" + ip + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
		return true;
	}

	private boolean checkAllowedUserAgent(String agent)
	{
		String[] allowedAgentsArray = allowedAgents.toLowerCase().split(",");
		if (allowedAgentsArray != null)
		{
			for (int i = 0; i < allowedAgentsArray.length; i++)
			{
				String allowedAgent = allowedAgentsArray[i].trim();
				if (StringUtils.isEmpty(allowedAgent))
					continue;
				if (agent.toLowerCase().startsWith(allowedAgent))
				{
					if(this.debugLog)
						getLogger().info(MODULE_NAME + ": UA will be ignored [" + agent + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
					return false;
				}
			}
		}
		if(this.debugLog)
			getLogger().info(MODULE_NAME + ": UA will be checked [" + agent + "]", WMSLoggerIDs.CAT_application, WMSLoggerIDs.EVT_comment);
		return true;
	}
}

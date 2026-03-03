/***************************************************************************************************
**    Copyright (C) 2018 - 2019 HMS Technology Center Ravensburg GmbH, all rights reserved
****************************************************************************************************
**
**        File: simply.cs
**     Summary: SimplyCAN API for C#
**              The simplyCAN API provides a simple programming interface for the development
**              of CAN applications on Windows PCs and on Linux PCs.
**
****************************************************************************************************
**    This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY.
***************************************************************************************************/
using System;
using System.Runtime.InteropServices;

/*##################################################################################################
 Name:
  IXXAT

 Description:
  Contains IXXAT product related type definitions.
##################################################################################################*/
namespace IXXAT
{
  /// <summary>
  /// Class SimplyCAN declares constants and function prototypes of IXXAT simplyCAN programming
  /// interface
  /// </summary>
  public class SimplyCAN
  {
    /// <summary>
    /// Name of the DLL that provides the simplyCAN API
    /// </summary>
    //public const string SimplyCANlib = "simplyCAN.dll"; //  Windows 32bit
    public const string SimplyCANlib = "simplyCAN.so"; //  LINUX32BIT
                                                           //public const string SimplyCANlib = "simplyCAN.so";//  ELF 64bit

#pragma warning disable 1591    // Missing XML comment for publicly visible type or member

    #region simply Constants

    /// <summary>
    /// CAN status definitions
    /// </summary>
    public const ushort CAN_STATUS_RUNNING = 0x01;
    public const ushort CAN_STATUS_RESET = 0x02;
    public const ushort CAN_STATUS_BUSOFF = 0x04;
    public const ushort CAN_STATUS_ERRORSTATUS = 0x08;
    public const ushort CAN_STATUS_RXOVERRUN = 0x10;
    public const ushort CAN_STATUS_TXOVERRUN = 0x20;
    public const ushort CAN_STATUS_PENDING = 0x40;

    /// <summary>
    /// Error codes of the API functions <para>
    /// Return value code 0 indicates that no error occured. </para><para>
    /// Actual errors have a negative value code. </para><para>
    /// Non-critical errors have a positive value code. </para>
    /// </summary>
    public const short SIMPLY_S_NO_ERROR = 0;   //  No error occurred
    public const short SIMPLY_E_SERIAL_OPEN = -1;   //  Unable to open the serial port
    public const short SIMPLY_E_SERIAL_ACCESS = -2;   //  Access on serial port denied
    public const short SIMPLY_E_SERIAL_CLOSED = -3;   //  Serial communication port is closed
    public const short SIMPLY_E_SERIAL_COMM = -4;   //  Serial communication error
    public const short SIMPLY_E_CMND_REQ_UNKNOWN = -5;   //  Command unknown on device
    public const short SIMPLY_E_CMND_RESP_TIMEOUT = -6;   //  Command response timeout reached
    public const short SIMPLY_E_CMND_RESP_UNEXPECTED = -7;   //  Unexpected command response received
    public const short SIMPLY_E_CMND_RESP_ERROR = -8;   //  Command response error
    public const short SIMPLY_E_INVALID_PROTOCOL_VERSION = -9;   //  Invalid simplyCAN protocol version
    public const short SIMPLY_E_INVALID_FW_VERSION = -10;   //  Invalid device firmware version
    public const short SIMPLY_E_INVALID_PRODUCT_STRING = -11;   //  Invalid simplyCAN product string
    public const short SIMPLY_E_CAN_INVALID_STATE = -12;   //  Invalid CAN state
    public const short SIMPLY_E_CAN_INVALID_BAUDRATE = -13;   //  Invalid CAN baud-rate
    public const short SIMPLY_E_TX_BUSY = -14;   //  Message could not be sent. TX is busy
    public const short SIMPLY_E_API_BUSY = -15;   //  API is busy

    #endregion

    #region simply Structures

    /// <summary>
    /// Device identification
    /// </summary>
    [StructLayout(LayoutKind.Sequential, Pack = 1, CharSet = CharSet.Ansi)]
    public struct identification_t
    {
      /// <summary>
      /// Firmware version string e.g. "1.00.00"
      /// </summary>
      [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 8)]
      public string fw_version;
      /// <summary>
      /// Hardware version string e.g. "1.00.00"
      /// </summary>
      [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 8)]
      public string hw_version;
      /// <summary>
      /// Product version string e.g. "1.00.00"
      /// </summary>
      [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 8)]
      public string product_version;
      /// <summary>
      /// Product name string e.g. "simplyCAN 1.0"
      /// </summary>
      [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 30)]
      public string product_string;
      /// <summary>
      /// Serial number e.g. "HW123456"
      /// </summary>
      [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 9)]
      public string serial_number;
    };

    /// <summary>
    /// CAN Message (classic CAN)
    /// </summary>
    [StructLayout(LayoutKind.Sequential, Pack = 1)]
    public struct can_msg_t
    {
      /// <summary>
      /// Timestamp in milliseconds
      /// </summary>
      public uint timestamp;
      /// <summary>
      /// CAN identifier (MSB=1: extended frame)
      /// </summary>
      public uint ident;
      /// <summary>
      /// Data Length Code (MSB=1: remote frame)
      /// </summary>
      public byte dlc;
      /// <summary>
      /// Data field of classic CAN, must be 8 bytes size
      /// </summary>
      [MarshalAs(UnmanagedType.ByValArray, SizeConst = 8)]
      public byte[] payload;
    };

    /// <summary>
    /// CAN Status
    /// </summary>
    [StructLayout(LayoutKind.Sequential, Pack = 1)]
    public struct can_sts_t
    {
      /// <summary>
      /// Bit coded status flags (see CAN status definitions)
      /// </summary>
      public ushort sts;
      /// <summary>
      /// Number of free elements in CAN message tx FIFO
      /// </summary>
      public ushort tx_free;
    };

    #endregion

#if NETCOREAPP2_0
#pragma warning disable CA1401  // P/Invoke method '' should not be visible
#endif

    #region simply Export Function Delegates

    /// <summary>
    /// Opens the serial communication interface. The message filter of the CAN controller is opened
    /// for all message identifiers.
    /// </summary>
    /// <param name="serial_port">
    /// Name of the serial communication port (e.g. COM1 or /dev/ttyACM0).
    /// Use the simplyCAN bus monitor to detect on which serial COM port the 
    /// simplyCAN is connected. With Windows it is also possible to use the 
    /// device manager and with Linux the command "ls -l /dev/serial/by-id".)
    /// </param>
    /// <returns><para>
    /// true - Function succeeded </para><para>
    /// false - Error occurred, call simply_get_last_error for more information. </para>
    /// </returns>
    [DllImport(SimplyCANlib)]
    [return: MarshalAs(UnmanagedType.I1)]
    public static extern bool simply_open([MarshalAs(UnmanagedType.LPArray, SizeConst = 256)]
                                            [In] char[] serial_port);

    /// <summary>
    /// Closes the serial communication interface and resets the CAN controller.
    /// </summary>
    /// <returns><para>
    /// true - Function succeeded </para><para>
    /// false - Error occurred, call simply_get_last_error for more information. </para>
    /// </returns>
    [DllImport(SimplyCANlib)]
    [return: MarshalAs(UnmanagedType.I1)]
    public static extern bool simply_close();

    /// <summary>
    /// Initializes the CAN controller.
    /// </summary>
    /// <param name="bitrate">
    /// CAN bitrate as integer value, possible values: 10, 20, 50, 125, 250, 500, 800, 1000
    /// </param>
    /// <returns><para>
    /// true - Function succeeded </para><para>
    /// false - Error occurred, call simply_get_last_error for more information. </para>
    /// </returns>
    [DllImport(SimplyCANlib)]
    [return: MarshalAs(UnmanagedType.I1)]
    public static extern bool simply_initialize_can([In] ushort bitrate);

    /// <summary>
    /// Gets firmware and hardware information about the simplyCAN device.
    /// </summary>
    /// <param name="p_identification">
    /// Identification record
    /// </param>
    /// <returns><para>
    /// true - Function succeeded </para><para>
    /// false - Error occurred, call simply_get_last_error for more information. </para>
    /// </returns>
    [DllImport(SimplyCANlib)]
    [return: MarshalAs(UnmanagedType.I1)]
    public static extern bool simply_identify([Out] out identification_t p_identification);

    /// <summary>
    /// Starts the CAN controller. Sets the CAN controller into running mode and clears the CAN
    /// message FIFOs. In running mode CAN messages can be transmitted and received.
    /// </summary>
    /// <returns><para>
    /// true - Function succeeded </para><para>
    /// false - Error occurred, call simply_get_last_error for more information. </para>
    /// </returns>
    [DllImport(SimplyCANlib)]
    [return: MarshalAs(UnmanagedType.I1)]
    public static extern bool simply_start_can();

    /// <summary>
    /// Stops the CAN controller. Sets the CAN controller into init mode. Does not reset the message
    /// filter of the CAN controller. Only stop the CAN controller when the CAN_STATUS_PENDING flag
    /// is not set.
    /// </summary>
    /// <returns><para>
    /// true - Function succeeded </para><para>
    /// false - Error occurred, call simply_get_last_error for more information. </para>
    /// </returns>
    [DllImport(SimplyCANlib)]
    [return: MarshalAs(UnmanagedType.I1)]
    public static extern bool simply_stop_can();

    /// <summary>
    /// Resets the CAN controller (hardware reset) and clears the message filter (open for all message
    /// identifiers). Sets the CAN controller into init mode.
    /// </summary>
    /// <returns><para>
    /// true - Function succeeded </para><para>
    /// false - Error occurred, call simply_get_last_error for more information. </para>
    /// </returns>
    [DllImport(SimplyCANlib)]
    [return: MarshalAs(UnmanagedType.I1)]
    public static extern bool simply_reset_can();

    /// <summary>
    /// Gets the status of the CAN controller.
    /// </summary>
    /// <param name="can_sts">
    /// Status as bit coded 16 bit value
    /// </param>
    /// <seealso cref="can_sts_t"/>
    /// <returns><para>
    /// true - Function succeeded </para><para>
    /// false - Error occurred, call simply_get_last_error for more information. </para>
    /// </returns>
    [DllImport(SimplyCANlib)]
    [return: MarshalAs(UnmanagedType.I1)]
    public static extern bool simply_can_status([In][Out] ref can_sts_t can_sts);

    /// <summary>
    /// Sets the 11 or 29 bit message filter of the CAN controller. To set the 29 bit message filter,
    /// the MSB in parameter value must be set.
    /// </summary>
    /// <param name="mask">
    /// 11 or 29 bit CAN message identifier mask
    /// </param>
    /// <param name="value">
    /// 11 or 29 bit CAN message identifier value, set MSB to set the 29 bit 
    /// message filter
    /// </param>
    /// <returns><para>
    /// true - Function succeeded </para><para>
    /// false - Error occurred, call simply_get_last_error for more information. </para>
    /// </returns>
    [DllImport(SimplyCANlib)]
    [return: MarshalAs(UnmanagedType.I1)]
    public static extern bool simply_set_filter([In] uint mask
                                                , [In] uint value);

    /// <summary>
    /// Receives a single CAN message.
    /// </summary>
    /// <param name="can_msg">
    /// Received CAN message
    /// </param>
    /// <returns><para>
    /// 1 - Message received </para><para>
    /// 0 - No message available in the receive queue </para><para>
    /// -1 - Error occurred, call simply_get_last_error for more information. </para>
    /// </returns>
    [DllImport(SimplyCANlib)]
    public static extern sbyte simply_receive([In][Out] ref can_msg_t can_msg);

    /// <summary>
    /// Writes a CAN message to the transmit FIFO. To check if the message is transmitted, request
    /// the CAN status with simply_can_status.
    /// </summary>
    /// <param name="can_msg">
    /// CAN message to be transmitted
    /// </param>
    /// <returns><para>
    /// true - Function succeeded </para><para>
    /// false - Error occurred, call simply_get_last_error for more information. </para>
    /// </returns>
    [DllImport(SimplyCANlib)]
    [return: MarshalAs(UnmanagedType.I1)]
    public static extern bool simply_send([In] ref can_msg_t can_msg);

    /// <summary>
    /// Gets the last error code. After reading the error code with simply_get_last_error the error
    /// code is set to 0. Each error can only be read once.
    /// </summary>
    /// <returns><para>
    /// 0 - No error occurred </para><para>
    /// else - Error occurred (see SIMPLY_E_XXX) </para>
    /// </returns>
    [DllImport(SimplyCANlib)]
    public static extern short simply_get_last_error();

    #endregion
  }
}
